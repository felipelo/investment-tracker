package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SmithManeuverHelocAccount")
public record HelocAccountSummaryResponse(
        Long id,
        String label,
        BigDecimal balance,
        BigDecimal creditLimit,
        BigDecimal interestRate,
        BigDecimal investmentUseBalance,
        BigDecimal tracedPct,
        String status
) {
}
