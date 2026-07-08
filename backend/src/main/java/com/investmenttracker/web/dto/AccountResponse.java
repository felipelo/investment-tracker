package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "Account")
public record AccountResponse(
        Long id,
        Long portfolioId,
        String label,
        String type,
        String institution,
        String currency,
        BigDecimal openingBalance,
        LocalDate openingBalanceDate,
        BigDecimal creditLimit,
        BigDecimal interestRate,
        BigDecimal currentBalance
) {
    public static AccountResponse from(Account account, BigDecimal currentBalance) {
        return new AccountResponse(
                account.getId(),
                account.getPortfolio().getId(),
                account.getLabel(),
                account.getType(),
                account.getInstitution(),
                account.getCurrency(),
                account.getOpeningBalance(),
                account.getOpeningBalanceDate(),
                account.getCreditLimit(),
                account.getInterestRate(),
                currentBalance
        );
    }
}
