package com.investmenttracker.domain;

import java.util.Set;

public enum CashTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    HELOC_DRAW,
    HELOC_REPAYMENT,
    INTEREST_CHARGE,
    INTEREST_PAYMENT,
    FEE;

    private static final Set<CashTransactionType> COUNTERPARTY_TYPES =
            Set.of(TRANSFER, HELOC_DRAW, HELOC_REPAYMENT);

    public boolean requiresCounterparty() {
        return COUNTERPARTY_TYPES.contains(this);
    }
}
