package io.github.davidmasdev.ledgerservice.transfer;

import io.github.davidmasdev.ledgerservice.journal.JournalEntryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/transfers")
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /** A transfer is recorded as a journal entry, so the new resource lives under {@code /journal-entries}. */
    @PostMapping
    ResponseEntity<JournalEntryResponse> transfer(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            UriComponentsBuilder uriBuilder) {
        var entry = transfers.transfer(idempotencyKey, request);
        var location = uriBuilder.path("/journal-entries/{id}").build(entry.id());
        return ResponseEntity.created(location).body(entry);
    }
}
