package com.investmenttracker.repository;

import com.investmenttracker.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            SELECT c.account.id, SUM(c.amount) FROM CashTransaction c
            WHERE c.account.id IN :ids
            GROUP BY c.account.id
            """)
    List<Object[]> sumAmountByAccountIds(@Param("ids") Collection<Long> ids);

    List<CashTransaction> findByTransferGroupId(String transferGroupId);

    boolean existsByAccountId(Long accountId);

    boolean existsByCounterpartyAccountId(Long accountId);
}
