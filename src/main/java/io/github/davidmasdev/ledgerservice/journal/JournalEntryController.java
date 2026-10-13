package io.github.davidmasdev.ledgerservice.journal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Journal entries")
class JournalEntryController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY_DESCRIPTION = "Unique per logical request; retries reuse it";

    private final JournalEntryService journal;

    JournalEntryController(JournalEntryService journal) {
        this.journal = journal;
    }

    @PostMapping
    @Operation(
            summary = "Post a balanced journal entry",
            description = "At least 2 postings that sum to zero per currency. Positive amounts are debits.")
    @ApiResponse(responseCode = "201", description = "Entry recorded (or the original response, on a replay)")
    @ApiResponse(responseCode = "400", description = "Invalid request or missing Idempotency-Key")
    @ApiResponse(
            responseCode = "422",
            description = "Unbalanced, insufficient funds, currency mismatch, unknown account, "
                    + "or the key was already used for a different request")
    ResponseEntity<JournalEntryResponse> post(
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
                    @RequestHeader(IDEMPOTENCY_KEY)
                    @NotBlank
                    @Size(max = 255)
                    String idempotencyKey,
            @Valid @RequestBody PostJournalEntryRequest request,
            UriComponentsBuilder uriBuilder) {
        return created(journal.post(idempotencyKey, request), uriBuilder);
    }

    @PostMapping("/{id}/reversal")
    @Operation(
            summary = "Reverse a journal entry",
            description = "Posts the opposite of every posting. An entry can be reversed at most once.")
    @ApiResponse(responseCode = "201", description = "Reversal recorded (or the original response, on a replay)")
    @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key")
    @ApiResponse(responseCode = "404", description = "No such journal entry")
    @ApiResponse(responseCode = "409", description = "The entry has already been reversed")
    @ApiResponse(
            responseCode = "422",
            description =
                    "The reversal would overdraw an account, " + "or the key was already used for a different request")
    ResponseEntity<JournalEntryResponse> reverse(
            @Parameter(description = IDEMPOTENCY_KEY_DESCRIPTION)
                    @RequestHeader(IDEMPOTENCY_KEY)
                    @NotBlank
                    @Size(max = 255)
                    String idempotencyKey,
            @PathVariable long id,
            UriComponentsBuilder uriBuilder) {
        return created(journal.reverse(idempotencyKey, id), uriBuilder);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a journal entry and its postings")
    @ApiResponse(responseCode = "200", description = "The journal entry")
    @ApiResponse(responseCode = "404", description = "No such journal entry")
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
