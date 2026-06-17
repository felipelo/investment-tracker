package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.service.PortfolioMetrics;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "Portfolio")
public record PortfolioResponse(
        Long id,
        String name,
        String description,
        String baseCurrency,
        String type,
        BigDecimal invested,
        BigDecimal marketValue,
        BigDecimal returnAmount,
        BigDecimal returnPct,
        int holdingsCount
) {
    public static PortfolioResponse from(Portfolio portfolio, PortfolioMetrics metrics) {
        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getBaseCurrency(),
                portfolio.getType(),
                metrics.invested(),
                metrics.marketValue(),
                metrics.returnAmount(),
                metrics.returnPct(),
                metrics.holdingsCount()
        );
    }
}
