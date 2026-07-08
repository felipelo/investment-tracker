package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "CreateSmithManeuverFlow")
public record CreateSmithManeuverFlowRequest(
        @NotNull Long portfolioId,
        @NotNull Long helocAccountId,
        String label,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Investment-use amount must be greater than zero")
        BigDecimal investmentUseAmount,
        List<Long> cashTransactionIds,
        Long securityTransactionId,
        String notes
) {
}
