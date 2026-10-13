package io.github.davidmasdev.ledgerservice.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @Operation(
            summary = "Open an account",
            description = "Accounts may not go negative unless allowNegative is true (e.g. a funding account).")
    @ApiResponse(responseCode = "201", description = "Account opened")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "An account with this name already exists")
    @ApiResponse(responseCode = "422", description = "Not a spendable ISO 4217 currency")
    ResponseEntity<AccountResponse> open(
            @Valid @RequestBody CreateAccountRequest request, UriComponentsBuilder uriBuilder) {
        var account = accounts.open(request);
        var location = uriBuilder.path("/accounts/{id}").build(account.id());
        return ResponseEntity.created(location).body(account);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account and its balance")
    @ApiResponse(responseCode = "200", description = "The account; its balance is derived from its postings")
    @ApiResponse(responseCode = "404", description = "No such account")
    AccountResponse get(@PathVariable long id) {
        return accounts.get(id);
    }
}
