package com.investmenttracker.repository;

import com.investmenttracker.domain.SecurityTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SecurityTransactionRepository extends JpaRepository<SecurityTransaction, Long> {

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            LEFT JOIN FETCH t.account
            WHERE (:securityId IS NULL OR t.security.id = :securityId)
              AND (:accountId IS NULL OR t.account.id = :accountId)
              AND (:from IS NULL OR t.date >= :from)
              AND (:to IS NULL OR t.date <= :to)
            ORDER BY t.date DESC, t.id DESC
            """)
    List<SecurityTransaction> findFiltered(
            @Param("securityId") Long securityId,
            @Param("accountId") Long accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            LEFT JOIN FETCH t.account
            WHERE t.id = :id
            """)
    Optional<SecurityTransaction> findByIdWithRelations(@Param("id") Long id);
}
