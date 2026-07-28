package com.investmenttracker.repository;

import com.investmenttracker.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {

    @Query("""
            SELECT c FROM CashTransaction c
            JOIN FETCH c.account
            LEFT JOIN FETCH c.counterpartyAccount
            WHERE c.account.portfolio.id = :portfolioId
            ORDER BY c.date DESC, c.id DESC
            """)
    List<CashTransaction> findByPortfolioId(@Param("portfolioId") Long portfolioId);

    @Query("""
            SELECT c FROM CashTransaction c
            JOIN FETCH c.account a
            WHERE c.type IN (com.investmenttracker.domain.CashTransactionType.FEE,
                             com.investmenttracker.domain.CashTransactionType.INTEREST_CHARGE,
                             com.investmenttracker.domain.CashTransactionType.INTEREST_PAYMENT)
              AND (:portfolioId IS NULL OR a.portfolio.id = :portfolioId)
              AND c.date >= :from
            ORDER BY c.date DESC, c.id DESC
            """)
    List<CashTransaction> findFeeAndInterestSince(
            @Param("portfolioId") Long portfolioId,
            @Param("from") LocalDate from
    );

    @Query("""
            SELECT c.account.id, SUM(c.amount) FROM CashTransaction c
            WHERE c.account.id IN :ids
            GROUP BY c.account.id
            """)
    List<Object[]> sumAmountByAccountIds(@Param("ids") Collection<Long> ids);

    List<CashTransaction> findByTransferGroupId(String transferGroupId);

    boolean existsByAccountId(Long accountId);

    boolean existsByCounterpartyAccountId(Long accountId);
}
