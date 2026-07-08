package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "SmithManeuverFlow")
public record SmithManeuverFlowResponse(
        Long id,
        Long portfolioId,
        Long helocAccountId,
        String helocAccountLabel,
        String label,
        BigDecimal investmentUseAmount,
        String status,
        String notes,
        List<FlowStepResponse> steps,
        Instant createdAt
) {
}
