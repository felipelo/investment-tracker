package com.investmenttracker.web.dto;

import com.investmenttracker.domain.CashPurpose;
import com.investmenttracker.domain.CashTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateCashTransaction")
public record CreateCashTransactionRequest(
        @NotNull Long accountId,
        @NotNull CashTransactionType type,
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.0", message = "Amount must be zero or greater") BigDecimal amount,
        CashPurpose purpose,
        Long counterpartyAccountId,
        String notes
) {
}
