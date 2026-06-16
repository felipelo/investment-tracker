package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Account;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Account")
public record AccountResponse(
        Long id,
        String label,
        String type,
        String currency
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getLabel(),
                account.getType(),
                account.getCurrency()
        );
    }
}
