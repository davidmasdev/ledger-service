package io.github.davidmasdev.ledgerservice.account;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByName(String name);

    /** The balance is derived from the postings, never stored, so it cannot drift from them. */
    @Query(value = "select coalesce(sum(amount), 0) from postings where account_id = :accountId", nativeQuery = true)
    BigDecimal balanceOf(long accountId);

    /**
     * Row-locks the accounts until the transaction ends. Always in ascending id order, so two transactions locking
     * overlapping sets of accounts cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockForUpdate(Collection<Long> ids);
}
