package com.investmenttracker.acb;

import com.investmenttracker.domain.Action;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic ACB calculator for one security's transaction history. Formulas
 * mirror the acb-tracker workbook ({@code build_acb_tracker.py}) and
 * REQUIREMENTS.md section 6.
 */
public final class AcbEngine {

    static final int MONEY_SCALE = 4;
    static final int SHARE_SCALE = 6;
    static final int ACB_PER_SHARE_SCALE = 8;
    static final int SUPERFICIAL_LOSS_WINDOW_DAYS = 30;

    private AcbEngine() {
    }

    public static List<ComputedTransactionRow> compute(List<SecurityTransactionInput> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return List.of();
        }

        var sorted = transactions.stream()
                .sorted(Comparator.comparing(SecurityTransactionInput::date)
                        .thenComparing(SecurityTransactionInput::id))
                .toList();

        var rows = new ArrayList<ComputedTransactionRow>(sorted.size());
        BigDecimal shareBalance = BigDecimal.ZERO;
        BigDecimal totalAcb = BigDecimal.ZERO;

        for (var input : sorted) {
            BigDecimal priorAcbPerShare = acbPerShare(totalAcb, shareBalance);
            BigDecimal shareChange = shareChange(input, shareBalance);
            BigDecimal acbChange = acbChange(input, priorAcbPerShare);
            BigDecimal deniedLoss = scaleMoney(nullToZero(input.deniedLossAdjustment()));

            shareBalance = scaleShares(shareBalance.add(shareChange));
            totalAcb = maxZero(scaleMoney(totalAcb.add(acbChange).add(deniedLoss)));

            BigDecimal currentAcbPerShare = acbPerShare(totalAcb, shareBalance);
            BigDecimal proceeds = proceeds(input);
            BigDecimal capitalGainLoss = capitalGainLoss(input, priorAcbPerShare, proceeds);

            rows.add(ComputedTransactionRow.from(
                    input,
                    shareChange,
                    shareBalance,
                    acbChange,
                    deniedLoss,
                    totalAcb,
                    currentAcbPerShare,
                    proceeds,
                    capitalGainLoss
            ));
        }

        return List.copyOf(flagSuperficialLosses(rows));
    }

    /**
     * Flags each loss-generating {@code SELL} that has a {@code BUY} of the same security within the
     * CRA superficial-loss window (+/-30 calendar days). Mirrors the acb-tracker workbook's column R
     * COUNTIFS detection. Callers compute one security at a time, so every row here is the same security.
     */
    private static List<ComputedTransactionRow> flagSuperficialLosses(List<ComputedTransactionRow> rows) {
        var buyDates = rows.stream()
                .filter(row -> row.action() == Action.BUY)
                .map(ComputedTransactionRow::date)
                .toList();

        var flagged = new ArrayList<ComputedTransactionRow>(rows.size());
        for (var row : rows) {
            boolean flag = row.action() == Action.SELL
                    && row.capitalGainLoss() != null
                    && row.capitalGainLoss().compareTo(BigDecimal.ZERO) < 0
                    && hasBuyWithinWindow(buyDates, row.date());
            flagged.add(row.withSuperficialLossFlag(flag));
        }
        return flagged;
    }

    private static boolean hasBuyWithinWindow(List<LocalDate> buyDates, LocalDate sellDate) {
        return buyDates.stream()
                .anyMatch(buyDate -> Math.abs(ChronoUnit.DAYS.between(buyDate, sellDate)) <= SUPERFICIAL_LOSS_WINDOW_DAYS);
    }

    public static HoldingSummary summarize(List<SecurityTransactionInput> transactions) {
        return HoldingSummary.fromRows(compute(transactions));
    }

    public static HoldingSummary summarizeRows(List<ComputedTransactionRow> computedRows) {
        return HoldingSummary.fromRows(computedRows);
    }

    private static BigDecimal shareChange(SecurityTransactionInput input, BigDecimal priorShareBalance) {
        return switch (input.action()) {
            case BUY -> scaleShares(requirePositive(input.shares(), "shares"));
            case SELL -> scaleShares(requirePositive(input.shares(), "shares").negate());
            case SPLIT -> {
                BigDecimal ratio = input.splitRatio();
                if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                    yield zeroShares();
                }
                yield scaleShares(priorShareBalance.multiply(ratio.subtract(BigDecimal.ONE)));
            }
            case RETURN_OF_CAPITAL, REINVESTED_DISTRIBUTION -> zeroShares();
        };
    }

    private static BigDecimal acbChange(SecurityTransactionInput input, BigDecimal priorAcbPerShare) {
        return switch (input.action()) {
            case BUY -> {
                BigDecimal shares = requirePositive(input.shares(), "shares");
                BigDecimal price = requireNonNegative(input.pricePerShare(), "pricePerShare");
                BigDecimal commission = nullToZero(input.commission());
                yield scaleMoney(shares.multiply(price).add(commission));
            }
            case SELL -> {
                BigDecimal shares = requirePositive(input.shares(), "shares");
                yield scaleMoney(shares.multiply(priorAcbPerShare).negate());
            }
            case RETURN_OF_CAPITAL -> {
                BigDecimal cash = requireCashAmount(input.cashAmount());
                yield scaleMoney(cash.negate());
            }
            case REINVESTED_DISTRIBUTION -> {
                BigDecimal cash = requireCashAmount(input.cashAmount());
                yield scaleMoney(cash);
            }
            case SPLIT -> zeroMoney();
        };
    }

    private static BigDecimal proceeds(SecurityTransactionInput input) {
        if (input.action() != Action.SELL) {
            return null;
        }
        BigDecimal shares = requirePositive(input.shares(), "shares");
        BigDecimal price = requireNonNegative(input.pricePerShare(), "pricePerShare");
        BigDecimal commission = nullToZero(input.commission());
        return scaleMoney(shares.multiply(price).subtract(commission));
    }

    private static BigDecimal capitalGainLoss(
            SecurityTransactionInput input,
            BigDecimal priorAcbPerShare,
            BigDecimal proceeds
    ) {
        if (input.action() != Action.SELL || proceeds == null) {
            return null;
        }
        BigDecimal shares = requirePositive(input.shares(), "shares");
        BigDecimal costBasis = scaleMoney(shares.multiply(priorAcbPerShare));
        return scaleMoney(proceeds.subtract(costBasis));
    }

    private static BigDecimal acbPerShare(BigDecimal totalAcb, BigDecimal shareBalance) {
        if (shareBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(ACB_PER_SHARE_SCALE, RoundingMode.HALF_UP);
        }
        return totalAcb.divide(shareBalance, ACB_PER_SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value;
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be zero or greater");
        }
        return value;
    }

    private static BigDecimal requireCashAmount(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("cashAmount is required");
        }
        return value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleShares(BigDecimal value) {
        return value.setScale(SHARE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return scaleMoney(value.max(BigDecimal.ZERO));
    }

    private static BigDecimal zeroShares() {
        return BigDecimal.ZERO.setScale(SHARE_SCALE, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
