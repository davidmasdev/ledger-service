package io.github.davidmasdev.ledgerservice.journal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/journal-entries")
class JournalEntryController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final JournalEntryService journal;

    JournalEntryController(JournalEntryService journal) {
        this.journal = journal;
    }

    @PostMapping
    ResponseEntity<JournalEntryResponse> post(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody PostJournalEntryRequest request,
            UriComponentsBuilder uriBuilder) {
        return created(journal.post(idempotencyKey, request), uriBuilder);
    }

    @PostMapping("/{id}/reversal")
    ResponseEntity<JournalEntryResponse> reverse(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @PathVariable long id,
            UriComponentsBuilder uriBuilder) {
        return created(journal.reverse(idempotencyKey, id), uriBuilder);
    }

    @GetMapping("/{id}")
    JournalEntryResponse get(@PathVariable long id) {
        return journal.get(id);
    }

    // A replay also answers 201 with the same Location, so it is indistinguishable from the original response.
    private static ResponseEntity<JournalEntryResponse> created(
            JournalEntryResponse entry, UriComponentsBuilder uriBuilder) {
        var location = uriBuilder.path("/journal-entries/{id}").build(entry.id());
        return ResponseEntity.created(location).body(entry);
    }
}
