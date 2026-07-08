package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateAccount")
public record CreateAccountRequest(
        @NotNull Long portfolioId,
        @NotBlank @Size(max = 128) String label,
        @NotBlank @Size(max = 24) String type,
        @Size(max = 128) String institution,
        @Size(min = 3, max = 3) String currency,
        @DecimalMin(value = "0.0", message = "Opening balance must be zero or greater") BigDecimal openingBalance,
        LocalDate openingBalanceDate,
        @DecimalMin(value = "0.0", message = "Credit limit must be zero or greater") BigDecimal creditLimit,
        @DecimalMin(value = "0.0", message = "Interest rate must be zero or greater") BigDecimal interestRate
) {
}
