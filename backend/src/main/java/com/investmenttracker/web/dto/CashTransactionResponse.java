package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.CashPurpose;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.SecurityTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "CashTransaction")
public record CashTransactionResponse(
        Long id,
        Long accountId,
        String accountLabel,
        CashTransactionType type,
        LocalDate date,
        BigDecimal amount,
        CashPurpose purpose,
        Long counterpartyAccountId,
        String counterpartyAccountLabel,
        String transferGroupId,
        String notes,
        Instant createdAt,
        BigDecimal balanceAfter,
        String source,
        Long securityTransactionId,
        String securityTicker,
        String tradeAction,
        Long dividendId
) {
    public static CashTransactionResponse from(CashTransaction transaction, BigDecimal balanceAfter) {
        Account counterparty = transaction.getCounterpartyAccount();
        return new CashTransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getLabel(),
                transaction.getType(),
                transaction.getDate(),
                transaction.getAmount(),
                transaction.getPurpose(),
                counterparty != null ? counterparty.getId() : null,
                counterparty != null ? counterparty.getLabel() : null,
                transaction.getTransferGroupId(),
                transaction.getNotes(),
                transaction.getCreatedAt(),
                balanceAfter,
                "CASH",
                null,
                null,
                null,
                null
        );
    }

    public static CashTransactionResponse fromTrade(SecurityTransaction transaction, BigDecimal balanceAfter) {
        return new CashTransactionResponse(
                null,
                transaction.getAccount().getId(),
                transaction.getAccount().getLabel(),
                null,
                transaction.getDate(),
                transaction.cashImpact(),
                null,
                null,
                null,
                null,
                transaction.getNotes(),
                transaction.getCreatedAt(),
                balanceAfter,
                "TRADE",
                transaction.getId(),
                transaction.getSecurity().getTicker(),
                transaction.getAction().name(),
                null
        );
    }

    public static CashTransactionResponse fromDividend(Dividend dividend, BigDecimal balanceAfter) {
        return new CashTransactionResponse(
                null,
                dividend.getAccount().getId(),
                dividend.getAccount().getLabel(),
                null,
                dividend.getPaymentDate(),
                dividend.cashImpact(),
                null,
                null,
                null,
                null,
                dividend.getNotes(),
                dividend.getCreatedAt(),
                balanceAfter,
                "DIVIDEND",
                null,
                dividend.getSecurity().getTicker(),
                null,
                dividend.getId()
        );
    }
}
