package io.github.davidmasdev.ledgerservice.account;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(
            @Valid @RequestBody CreateAccountRequest request, UriComponentsBuilder uriBuilder) {
        var account = accounts.open(request);
        var location = uriBuilder.path("/accounts/{id}").build(account.id());
        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable long id) {
        return accounts.get(id);
    }
}
