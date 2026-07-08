package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Aggregated per-portfolio dashboard payload (REQUIREMENTS.md section 5). */
@Schema(name = "Dashboard")
public record DashboardResponse(
        BigDecimal portfolioValue,
        BigDecimal invested,
        LocalDate asOfDate,
        ReturnFigure todaysReturn,
        ReturnFigure allTimeReturn,
        List<PeriodReturn> periodReturns,
        List<AllocationSlice> allocation
) {
    @Schema(name = "DashboardReturnFigure")
    public record ReturnFigure(
            BigDecimal amount,
            BigDecimal pct,
            LocalDate basisDate,
            boolean available
    ) {
        public static ReturnFigure unavailable() {
            return new ReturnFigure(null, null, null, false);
        }
    }

    @Schema(name = "DashboardPeriodReturn")
    public record PeriodReturn(
            String label,
            BigDecimal amount,
            BigDecimal pct,
            boolean available
    ) {
    }

    @Schema(name = "DashboardAllocationSlice")
    public record AllocationSlice(
            Long securityId,
            String ticker,
            String name,
            BigDecimal marketValue,
            BigDecimal pct
    ) {
    }
}
