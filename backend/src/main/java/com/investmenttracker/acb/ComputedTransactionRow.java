package com.investmenttracker.acb;

import com.investmenttracker.domain.Action;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One transaction with running ACB fields, aligned to the acb-tracker workbook columns. */
public record ComputedTransactionRow(
        long transactionId,
        LocalDate date,
        Action action,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal commission,
        BigDecimal cashAmount,
        BigDecimal splitRatio,
        String notes,
        BigDecimal shareChange,
        BigDecimal shareBalance,
        BigDecimal acbChange,
        BigDecimal deniedLossAdjustment,
        BigDecimal totalAcb,
        BigDecimal acbPerShare,
        BigDecimal proceeds,
        BigDecimal capitalGainLoss
) {
    static ComputedTransactionRow from(
            SecurityTransactionInput input,
            BigDecimal shareChange,
            BigDecimal shareBalance,
            BigDecimal acbChange,
            BigDecimal deniedLossAdjustment,
            BigDecimal totalAcb,
            BigDecimal acbPerShare,
            BigDecimal proceeds,
            BigDecimal capitalGainLoss
    ) {
        return new ComputedTransactionRow(
                input.id(),
                input.date(),
                input.action(),
                input.shares(),
                input.pricePerShare(),
                input.commission(),
                input.cashAmount(),
                input.splitRatio(),
                input.notes(),
                shareChange,
                shareBalance,
                acbChange,
                deniedLossAdjustment,
                totalAcb,
                acbPerShare,
                proceeds,
                capitalGainLoss
        );
    }
}
