package com.investmenttracker.repository;

import com.investmenttracker.domain.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DividendRepository extends JpaRepository<Dividend, Long> {

    @Query("""
            SELECT d FROM Dividend d
            JOIN FETCH d.security
            LEFT JOIN FETCH d.account
            WHERE d.portfolio.id = :portfolioId
            ORDER BY d.paymentDate DESC, d.id DESC
            """)
    List<Dividend> findByPortfolioIdOrderByPaymentDateDesc(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT d FROM Dividend d
            JOIN FETCH d.security
            JOIN FETCH d.account a
            WHERE a.id IN :accountIds
              AND d.drip = false
            """)
    List<Dividend> findCashImpactingByAccountIds(@Param("accountIds") Collection<Long> accountIds);

    @Query("""
            SELECT d FROM Dividend d
            JOIN FETCH d.security
            JOIN FETCH d.account a
            WHERE a.portfolio.id = :portfolioId
              AND d.drip = false
            """)
    List<Dividend> findCashImpactingByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT COALESCE(SUM(d.grossAmount - d.withholdingTax), 0)
            FROM Dividend d
            WHERE d.portfolio.id = :portfolioId
            """)
    java.math.BigDecimal sumNetByPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT COALESCE(SUM(d.grossAmount - d.withholdingTax), 0)
            FROM Dividend d
            WHERE d.portfolio.id = :portfolioId
              AND d.paymentDate > :after
              AND d.paymentDate <= :through
            """)
    java.math.BigDecimal sumNetByPortfolioBetween(
            @Param("portfolioId") Long portfolioId,
            @Param("after") java.time.LocalDate after,
            @Param("through") java.time.LocalDate through
    );

    @Query("""
            SELECT d FROM Dividend d
            WHERE d.portfolio.id = :portfolioId
              AND EXTRACT(YEAR FROM d.paymentDate) = :year
            ORDER BY d.paymentDate ASC, d.id ASC
            """)
    List<Dividend> findByPortfolioIdAndYear(@Param("portfolioId") Long portfolioId, @Param("year") int year);

    @Query("""
            SELECT DISTINCT EXTRACT(YEAR FROM d.paymentDate)
            FROM Dividend d
            WHERE d.portfolio.id = :portfolioId
            ORDER BY EXTRACT(YEAR FROM d.paymentDate) DESC
            """)
    List<Integer> findDistinctYears(@Param("portfolioId") Long portfolioId);
}
