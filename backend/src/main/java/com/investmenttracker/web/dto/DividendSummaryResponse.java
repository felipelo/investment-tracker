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
        List<Integer> availableYears
) {
}
