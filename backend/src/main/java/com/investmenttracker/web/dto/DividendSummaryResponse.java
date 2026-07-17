package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Monthly dividend totals (net) for a calendar year plus a running cumulative
 * series, used by the dashboard dividends chart.
 */
@Schema(name = "DividendSummary")
public record DividendSummaryResponse(
        int year,
        List<BigDecimal> months,
        List<BigDecimal> cumulative,
        BigDecimal ytdTotal,
        List<Integer> availableYears,
        List<List<MonthSlice>> breakdown
) {

    /**
     * A single holding's net dividend contribution within one month. Slices are
     * ordered by amount descending so the biggest payer stacks at the bottom.
     */
    public record MonthSlice(long securityId, String ticker, String name, BigDecimal amount) {
    }
}
