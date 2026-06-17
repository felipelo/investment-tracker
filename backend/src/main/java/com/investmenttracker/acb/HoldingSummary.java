package com.investmenttracker.acb;

import com.investmenttracker.domain.Action;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** End-of-chain position for one security after all transactions are applied. */
public record HoldingSummary(
        BigDecimal shareBalance,
        BigDecimal totalAcb,
        BigDecimal acbPerShare,
        BigDecimal investedCost,
        LocalDate lastTransactionDate
) {
    public static HoldingSummary empty() {
        return new HoldingSummary(
                BigDecimal.ZERO.setScale(6, RoundingMode.UNNECESSARY),
                BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),
                BigDecimal.ZERO.setScale(8, RoundingMode.UNNECESSARY),
                BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),
                null
        );
    }

    public static HoldingSummary fromRows(List<ComputedTransactionRow> rows) {
        if (rows.isEmpty()) {
            return empty();
        }
        var last = rows.getLast();
        BigDecimal investedCost = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        for (var row : rows) {
            if (row.action() == Action.BUY) {
                investedCost = investedCost.add(row.acbChange());
            } else if (row.action() == Action.SELL && row.proceeds() != null) {
                investedCost = investedCost.subtract(row.proceeds());
            }
        }
        return new HoldingSummary(
                last.shareBalance(),
                last.totalAcb(),
                last.acbPerShare(),
                investedCost,
                last.date()
        );
    }
}
