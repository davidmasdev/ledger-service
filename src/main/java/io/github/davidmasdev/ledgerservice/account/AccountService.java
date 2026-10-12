package io.github.davidmasdev.ledgerservice.account;

import io.github.davidmasdev.ledgerservice.shared.ConflictException;
import io.github.davidmasdev.ledgerservice.shared.NotFoundException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public AccountResponse open(CreateAccountRequest request) {
        // Friendly error for the common case; the unique constraint still catches concurrent duplicates.
        if (accounts.existsByName(request.name())) {
            throw new ConflictException("An account named '%s' already exists".formatted(request.name()));
        }
        var account = accounts.save(new Account(request.name(), request.currency()));
        return AccountResponse.from(account, BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(long id) {
        var account =
                accounts.findById(id).orElseThrow(() -> new NotFoundException("Account %d not found".formatted(id)));
        return AccountResponse.from(account, accounts.balanceOf(id));
    }
}
