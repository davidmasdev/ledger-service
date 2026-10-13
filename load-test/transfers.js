// k6 load test for POST /transfers. See README.md for how to run it and RESULTS.md for measured results.
//
//   RATE      transfers started per second in the measured phase (default 200)
//   SCENARIO  "spread" (default): random pairs of accounts; "hot": every transfer leaves the same account
//   ACCOUNTS  number of funded accounts to spread transfers over (default 200)
//   BASE_URL  default http://localhost:8080
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 200);
const SCENARIO = __ENV.SCENARIO || 'spread';
const ACCOUNTS = Number(__ENV.ACCOUNTS || 200);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Open model: k6 starts RATE iterations per second whether or not earlier ones have finished, so a slow service shows
// up as latency and dropped iterations instead of silently lowering the request rate.
function atRate(startTime, duration) {
    return {
        executor: 'constant-arrival-rate',
        rate: RATE,
        timeUnit: '1s',
        duration,
        startTime,
        preAllocatedVUs: Math.max(50, Math.ceil(RATE / 5)),
        maxVUs: Math.max(200, RATE),
    };
}

export const options = {
    scenarios: {
        // Lets the JVM warm up (JIT, connection pools); excluded from the thresholds below.
        warmup: atRate('0s', '30s'),
        measure: atRate('30s', '2m'),
    },
    // Only the measured phase counts. A run "sustains" RATE if all of these pass.
    thresholds: {
        'http_req_failed{scenario:measure}': ['rate==0'],
        'http_req_duration{scenario:measure}': ['p(95)<100'],
        'dropped_iterations{scenario:measure}': ['count==0'],
        'checks{scenario:measure}': ['rate==1'],
    },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function post(path, body, headers = {}) {
    return http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers: { ...JSON_HEADERS, ...headers } });
}

function openAccount(name, allowNegative) {
    const res = post('/accounts', { name, currency: 'USD', allowNegative });
    if (res.status !== 201) {
        throw new Error(`Could not open account: ${res.status} ${res.body}`);
    }
    return res.json('id');
}

// Runs once before the scenarios: funds every account generously, so no transfer fails for insufficient funds and
// every non-201 response is a real error.
export function setup() {
    const run = `${Date.now()}`;
    const funding = openAccount(`load-funding-${run}`, true);
    const accounts = [];
    for (let i = 0; i < ACCOUNTS; i++) {
        const id = openAccount(`load-${run}-${i}`, false);
        const res = post(
            '/journal-entries',
            {
                description: 'load test funding',
                postings: [
                    { accountId: funding, currency: 'USD', amount: '-1000000.00' },
                    { accountId: id, currency: 'USD', amount: '1000000.00' },
                ],
            },
            { 'Idempotency-Key': `load-funding-${run}-${i}` },
        );
        if (res.status !== 201) {
            throw new Error(`Could not fund account ${id}: ${res.status} ${res.body}`);
        }
        accounts.push(id);
    }
    return { accounts };
}

function randomIndex(n) {
    return Math.floor(Math.random() * n);
}

export default function ({ accounts }) {
    // "hot": every transfer debits accounts[0], so all of them queue on that account's row lock.
    const fromIndex = SCENARIO === 'hot' ? 0 : randomIndex(accounts.length);
    let toIndex = randomIndex(accounts.length - 1);
    if (toIndex >= fromIndex) {
        toIndex++; // never the same account as fromIndex
    }
    const amount = ((randomIndex(100) + 1) / 100).toFixed(2); // 0.01 .. 1.00

    const res = post(
        '/transfers',
        { fromAccountId: accounts[fromIndex], toAccountId: accounts[toIndex], currency: 'USD', amount },
        { 'Idempotency-Key': `${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}` },
    );
    check(res, { 'transfer created': (r) => r.status === 201 });
}
