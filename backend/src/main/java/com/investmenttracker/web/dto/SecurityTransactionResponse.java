package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.SecurityTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "SecurityTransaction")
public record SecurityTransactionResponse(
        Long id,
        LocalDate date,
        Long securityId,
        Long accountId,
        Action action,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal commission,
        BigDecimal cashAmount,
        BigDecimal splitRatio,
        BigDecimal deniedLossAdjustment,
        String notes,
        Instant createdAt
) {
    public static SecurityTransactionResponse from(SecurityTransaction transaction) {
        return new SecurityTransactionResponse(
                transaction.getId(),
                transaction.getDate(),
                transaction.getSecurity().getId(),
                transaction.getAccount() != null ? transaction.getAccount().getId() : null,
                transaction.getAction(),
                transaction.getShares(),
                transaction.getPricePerShare(),
                transaction.getCommission(),
                transaction.getCashAmount(),
                transaction.getSplitRatio(),
                transaction.getDeniedLossAdjustment(),
                transaction.getNotes(),
                transaction.getCreatedAt()
        );
    }
}
