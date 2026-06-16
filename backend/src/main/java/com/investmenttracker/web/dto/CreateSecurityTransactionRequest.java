package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Action;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateSecurityTransaction")
public record CreateSecurityTransactionRequest(
        @NotNull LocalDate date,
        @NotNull Long securityId,
        Long accountId,
        @NotNull Action action,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal commission,
        BigDecimal cashAmount,
        BigDecimal splitRatio,
        String notes
) {
}
