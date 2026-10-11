package io.github.davidmasdev.ledgerservice.journal;

import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    boolean existsByReversalOf(long journalEntryId);
}
