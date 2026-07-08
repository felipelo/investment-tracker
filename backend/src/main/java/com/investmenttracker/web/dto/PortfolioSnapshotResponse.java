package com.investmenttracker.web.dto;

import com.investmenttracker.domain.PortfolioSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "PortfolioSnapshot")
public record PortfolioSnapshotResponse(
        Long id,
        Long portfolioId,
        LocalDate date,
        BigDecimal marketValue,
        Instant createdAt
) {
    public static PortfolioSnapshotResponse from(PortfolioSnapshot snapshot) {
        return new PortfolioSnapshotResponse(
                snapshot.getId(),
                snapshot.getPortfolio().getId(),
                snapshot.getSnapshotDate(),
                snapshot.getMarketValue(),
                snapshot.getCreatedAt()
        );
    }
}
