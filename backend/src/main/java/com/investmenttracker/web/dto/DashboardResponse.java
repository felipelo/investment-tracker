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
        ReturnFigure priceReturn,
        ReturnFigure dividendReturn,
        List<PeriodReturn> periodReturns,
        List<HoldingReturnBreakdown> holdingBreakdowns,
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
            BigDecimal priceAmount,
            BigDecimal pricePct,
            BigDecimal dividendAmount,
            BigDecimal dividendPct,
            boolean available
    ) {
    }

    /** Per-security split of the price and dividend returns, so the dashboard rows can expand per ETF. */
    @Schema(name = "DashboardHoldingReturnBreakdown")
    public record HoldingReturnBreakdown(
            Long securityId,
            String ticker,
            String name,
            ReturnFigure priceReturn,
            ReturnFigure dividendReturn,
            List<PeriodReturn> periodReturns
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
