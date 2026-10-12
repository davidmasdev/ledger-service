package io.github.davidmasdev.ledgerservice.journal;

/** The operation that produced a journal entry. */
public enum EntryKind {
    /** Posted directly through {@code POST /journal-entries}. */
    ENTRY,
    /** Cancels an earlier entry; created only by {@link JournalEntry#reversalOf}. */
    REVERSAL,
    /** Moves money between two accounts through {@code POST /transfers}. */
    TRANSFER
}
