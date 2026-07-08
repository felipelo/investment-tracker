package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** One rendered step of a Smith Maneuver flow chain. */
@Schema(name = "SmithManeuverFlowStep")
public record FlowStepResponse(
        int order,
        String kind,
        String stepLabel,
        BigDecimal amount,
        String ticker,
        String purpose,
        String detail,
        Long cashTransactionId,
        Long securityTransactionId
) {
}
