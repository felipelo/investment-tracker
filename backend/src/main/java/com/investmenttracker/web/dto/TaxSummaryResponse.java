package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-portfolio, per-tax-year summary (REQUIREMENTS.md section 6.6): realized capital
 * gains by security, dividend income by security, and the Smith Maneuver interest
 * summary by month. A record-keeping aid, not tax advice (REQUIREMENTS.md section 6.7).
 */
@Schema(name = "TaxSummary")
public record TaxSummaryResponse(
        int year,
        List<Integer> availableYears,
        RealizedGains realizedGains,
        DividendIncome dividends,
        InterestSummary interest
) {
    @Schema(name = "TaxRealizedGains")
    public record RealizedGains(
            List<RealizedGainRow> rows,
            RealizedGainRow total
    ) {
    }

    @Schema(name = "TaxRealizedGainRow")
    public record RealizedGainRow(
            Long securityId,
            String ticker,
            String name,
            int dispositions,
            BigDecimal proceeds,
            BigDecimal acbDisposed,
            BigDecimal gainLoss
    ) {
    }

    @Schema(name = "TaxDividendIncome")
    public record DividendIncome(
            List<DividendRow> rows,
            DividendRow total
    ) {
    }

    @Schema(name = "TaxDividendRow")
    public record DividendRow(
            Long securityId,
            String ticker,
            String name,
            BigDecimal gross,
            BigDecimal withholding,
            BigDecimal net
    ) {
    }

    @Schema(name = "TaxInterestSummary")
    public record InterestSummary(
            List<InterestMonthRow> months,
            InterestMonthRow ytd
    ) {
    }

    @Schema(name = "TaxInterestMonthRow")
    public record InterestMonthRow(
            String month,
            BigDecimal charged,
            BigDecimal deductibleEstimate
    ) {
    }
}
