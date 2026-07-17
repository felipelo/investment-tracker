package com.investmenttracker.repository;

import com.investmenttracker.domain.SecurityTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SecurityTransactionRepository extends JpaRepository<SecurityTransaction, Long> {

    @Query("""
            SELECT DISTINCT a.portfolio.id FROM SecurityTransaction t
            JOIN t.account a
            WHERE t.security.id IN :securityIds
            """)
    List<Long> findPortfolioIdsHoldingSecurities(@Param("securityIds") Collection<Long> securityIds);

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            LEFT JOIN FETCH t.account a
            WHERE (:portfolioId IS NULL OR a.portfolio.id = :portfolioId)
              AND (:securityId IS NULL OR t.security.id = :securityId)
              AND (:accountId IS NULL OR t.account.id = :accountId)
              AND (:from IS NULL OR t.date >= :from)
              AND (:to IS NULL OR t.date <= :to)
            ORDER BY t.date DESC, t.id DESC
            """)
    List<SecurityTransaction> findFiltered(
            @Param("portfolioId") Long portfolioId,
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

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            JOIN FETCH t.account a
            WHERE a.id IN :accountIds
              AND t.action IN (com.investmenttracker.domain.Action.BUY,
                               com.investmenttracker.domain.Action.SELL)
            """)
    List<SecurityTransaction> findCashImpactingByAccountIds(@Param("accountIds") Collection<Long> accountIds);

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            JOIN FETCH t.account a
            WHERE a.portfolio.id = :portfolioId
              AND t.action IN (com.investmenttracker.domain.Action.BUY,
                               com.investmenttracker.domain.Action.SELL)
            """)
    List<SecurityTransaction> findCashImpactingByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security s
            JOIN FETCH t.account a
            WHERE a.portfolio.id = :portfolioId
            ORDER BY s.ticker ASC, t.date ASC, t.id ASC
            """)
    List<SecurityTransaction> findAllForHoldingsByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT t FROM SecurityTransaction t
            JOIN FETCH t.security
            JOIN FETCH t.account a
            WHERE t.security.id = :securityId
              AND a.portfolio.id = :portfolioId
            ORDER BY t.date ASC, t.id ASC
            """)
    List<SecurityTransaction> findBySecurityIdAndPortfolioForHoldings(
            @Param("securityId") Long securityId,
            @Param("portfolioId") Long portfolioId
    );

    boolean existsByAccountId(Long accountId);
}
