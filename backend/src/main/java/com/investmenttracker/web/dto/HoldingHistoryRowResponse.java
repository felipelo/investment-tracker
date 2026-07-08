package com.investmenttracker.web.dto;

import com.investmenttracker.acb.ComputedTransactionRow;
import com.investmenttracker.domain.Action;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "HoldingHistoryRow")
public record HoldingHistoryRowResponse(
        Long transactionId,
        LocalDate date,
        Action action,
        BigDecimal shares,
        BigDecimal pricePerShare,
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
        BigDecimal capitalGainLoss,
        boolean superficialLossFlag
) {
    public static HoldingHistoryRowResponse from(ComputedTransactionRow row) {
        return new HoldingHistoryRowResponse(
                row.transactionId(),
                row.date(),
                row.action(),
                row.shares(),
                row.pricePerShare(),
                row.cashAmount(),
                row.splitRatio(),
                row.notes(),
                row.shareChange(),
                row.shareBalance(),
                row.acbChange(),
                row.deniedLossAdjustment(),
                row.totalAcb(),
                row.acbPerShare(),
                row.proceeds(),
                row.capitalGainLoss(),
                row.superficialLossFlag()
        );
    }
}
