package com.investmenttracker.repository;

import com.investmenttracker.domain.SmithManeuverFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SmithManeuverFlowRepository extends JpaRepository<SmithManeuverFlow, Long> {

    @Query("""
            SELECT DISTINCT f FROM SmithManeuverFlow f
            JOIN FETCH f.helocAccount
            LEFT JOIN FETCH f.steps
            WHERE f.portfolio.id = :portfolioId
            ORDER BY f.createdAt DESC
            """)
    List<SmithManeuverFlow> findByPortfolioIdWithSteps(@Param("portfolioId") Long portfolioId);

    boolean existsByHelocAccountId(Long accountId);
}
