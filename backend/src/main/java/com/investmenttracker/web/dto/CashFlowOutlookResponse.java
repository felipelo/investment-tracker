package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Projected cash flow over the next few weeks: dividends coming in against fees and interest going
 * out, both inferred from recorded history. Every event is an estimate, never a confirmed charge.
 */
@Schema(name = "CashFlowOutlook")
public record CashFlowOutlookResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal moneyIn,
        BigDecimal moneyOut,
        BigDecimal net,
        BigDecimal lowestRunningTotal,
        LocalDate lowestOn,
        List<WeekBucket> weeks,
        List<CashFlowEvent> events,
        List<String> warnings
) {
    @Schema(name = "CashFlowWeekBucket")
    public record WeekBucket(LocalDate weekStart, BigDecimal in, BigDecimal out, BigDecimal runningTotal) {
    }

    /** {@code amount} is signed: positive lands as cash, negative leaves. */
    @Schema(name = "CashFlowEvent")
    public record CashFlowEvent(
            LocalDate date,
            String label,
            String category,
            BigDecimal amount,
            BigDecimal runningTotal
    ) {
    }
}
