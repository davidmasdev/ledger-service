package io.github.davidmasdev.ledgerservice.account;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByName(String name);

    /** The balance is derived from the postings, never stored, so it cannot drift from them. */
    @Query(value = "select coalesce(sum(amount), 0) from postings where account_id = :accountId", nativeQuery = true)
    BigDecimal balanceOf(long accountId);
}
