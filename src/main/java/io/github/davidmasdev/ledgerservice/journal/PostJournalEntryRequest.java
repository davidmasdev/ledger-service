package io.github.davidmasdev.ledgerservice.journal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostJournalEntryRequest(
        @NotBlank @Size(max = 500) String description,
        @NotNull @Size(min = 2, max = 100) List<@Valid @NotNull PostingRequest> postings) {}
