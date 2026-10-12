-- Accounts with allow_negative = false must never have a negative balance. Some accounts must be allowed to go
-- negative: all balances in a currency sum to zero, so money enters the ledger from such an account.
alter table accounts add column allow_negative boolean not null default false;
