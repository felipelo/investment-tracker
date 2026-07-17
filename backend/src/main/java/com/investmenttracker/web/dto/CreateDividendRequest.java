package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateDividend")
public record CreateDividendRequest(
        @NotNull Long portfolioId,
        @NotNull Long securityId,
        Long accountId,
        @NotNull LocalDate paymentDate,
        @NotNull @DecimalMin(value = "0.0", message = "Gross amount must be zero or greater") BigDecimal grossAmount,
        @DecimalMin(value = "0.0", message = "Withholding tax must be zero or greater") BigDecimal withholdingTax,
        @Size(min = 3, max = 3) String currency,
        Boolean drip,
        Long reinvestmentTransactionId,
        String notes
) {
}
