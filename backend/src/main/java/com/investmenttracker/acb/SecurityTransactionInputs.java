package com.investmenttracker.acb;

import com.investmenttracker.domain.SecurityTransaction;

/** Maps persisted transactions into {@link SecurityTransactionInput} for the ACB engine. */
public final class SecurityTransactionInputs {

    private SecurityTransactionInputs() {
    }

    public static SecurityTransactionInput from(SecurityTransaction transaction) {
        return new SecurityTransactionInput(
                transaction.getId(),
                transaction.getDate(),
                transaction.getAction(),
                transaction.getShares(),
                transaction.getPricePerShare(),
                transaction.getCommission(),
                transaction.getCashAmount(),
                transaction.getSplitRatio(),
                transaction.getDeniedLossAdjustment(),
                transaction.getNotes()
        );
    }
}
