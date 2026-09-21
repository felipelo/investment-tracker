package com.investmenttracker.service;

import com.investmenttracker.acb.AcbEngine;
import com.investmenttracker.acb.SecurityTransactionInput;
import com.investmenttracker.acb.SecurityTransactionInputs;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.SecurityTransaction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Time-weighted returns over a window for any subset of securities.
 *
 * <p>The value series is cut at every external cash flow (a BUY or SELL), and each sub-period return is
 * chained, so buying more of a holding never registers as performance. Splits and reinvested
 * distributions are not cuts: they change shares without new money, so they belong inside a sub-period.
 * Dividends are treated as income received at the end of the sub-period containing their payment date;
 * the dividend percent is the difference between the total and price-only chains.
 */
final class PerformanceWindow {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;
    private static final MathContext CHAIN = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** One period's split of returns; {@code null} percents mean there was no basis to measure against. */
    record Result(
            boolean available,
            BigDecimal priceAmount,
            BigDecimal pricePct,
            BigDecimal dividendAmount,
            BigDecimal dividendPct,
            BigDecimal totalAmount,
            BigDecimal totalPct
    ) {
        static Result unavailable() {
            return new Result(false, null, null, null, null, null, null);
        }
    }

    private final Map<Long, List<SecurityTransactionInput>> inputsBySecurity = new LinkedHashMap<>();
    private final Map<Long, NavigableMap<LocalDate, BigDecimal>> pricesBySecurity = new LinkedHashMap<>();
    private final Map<Long, List<Dividend>> dividendsBySecurity = new LinkedHashMap<>();

    PerformanceWindow(
            List<SecurityTransaction> transactions,
            List<Dividend> dividends,
            List<PriceSnapshot> snapshots
    ) {
        for (var transaction : transactions) {
            inputsBySecurity
                    .computeIfAbsent(transaction.getSecurity().getId(), key -> new java.util.ArrayList<>())
                    .add(SecurityTransactionInputs.from(transaction));
        }
        for (var snapshot : snapshots) {
            pricesBySecurity
                    .computeIfAbsent(snapshot.getSecurity().getId(), key -> new TreeMap<>())
                    .put(snapshot.getSnapshotDate(), snapshot.getPrice());
        }
        for (var dividend : dividends) {
            dividendsBySecurity
                    .computeIfAbsent(dividend.getSecurity().getId(), key -> new java.util.ArrayList<>())
                    .add(dividend);
        }
    }

    Set<Long> securityIds() {
        return inputsBySecurity.keySet();
    }

    Map<Long, List<Dividend>> dividendsBySecurity() {
        return dividendsBySecurity;
    }

    /** Chained return for {@code securityIds} over {@code (start, end]}, valued from {@code start}. */
    Result compute(Collection<Long> securityIds, LocalDate start, LocalDate end) {
        BigDecimal startValue = valueAt(securityIds, start, true);
        if (startValue == null || startValue.compareTo(BigDecimal.ZERO) == 0) {
            // Nothing held when the window opens: the chain could only measure from the first purchase, which
            // would report a shorter period than the column claims. Callers surface that as unavailable.
            return Result.unavailable();
        }

        BigDecimal priceChain = BigDecimal.ONE;
        BigDecimal totalChain = BigDecimal.ONE;
        BigDecimal flowSum = BigDecimal.ZERO;
        BigDecimal subPeriodStart = startValue;
        LocalDate previousDate = start;

        for (var flowDate : flowDates(securityIds, start, end)) {
            BigDecimal beforeFlow = valueAt(securityIds, flowDate, false);
            BigDecimal afterFlow = valueAt(securityIds, flowDate, true);
            if (beforeFlow == null || afterFlow == null) {
                return Result.unavailable();
            }
            BigDecimal income = dividendsBetween(securityIds, previousDate, flowDate);
            if (subPeriodStart.compareTo(BigDecimal.ZERO) > 0) {
                priceChain = priceChain.multiply(beforeFlow.divide(subPeriodStart, CHAIN), CHAIN);
                totalChain = totalChain.multiply(beforeFlow.add(income).divide(subPeriodStart, CHAIN), CHAIN);
            }
            flowSum = flowSum.add(afterFlow.subtract(beforeFlow));
            subPeriodStart = afterFlow;
            previousDate = flowDate;
        }

        BigDecimal endValue = valueAt(securityIds, end, true);
        if (endValue == null) {
            return Result.unavailable();
        }
        BigDecimal trailingIncome = dividendsBetween(securityIds, previousDate, end);
        if (subPeriodStart.compareTo(BigDecimal.ZERO) > 0) {
            priceChain = priceChain.multiply(endValue.divide(subPeriodStart, CHAIN), CHAIN);
            totalChain = totalChain.multiply(endValue.add(trailingIncome).divide(subPeriodStart, CHAIN), CHAIN);
        }

        BigDecimal priceAmount = endValue.subtract(startValue).subtract(flowSum).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal dividendAmount = dividendsBetween(securityIds, start, end).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalAmount = priceAmount.add(dividendAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal pricePct = toPercent(priceChain);
        BigDecimal totalPct = toPercent(totalChain);
        return new Result(true, priceAmount, pricePct, dividendAmount, totalPct.subtract(pricePct), totalAmount, totalPct);
    }

    private static BigDecimal toPercent(BigDecimal chain) {
        return chain.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** Dates in {@code (after, through]} where money entered or left these positions. */
    private List<LocalDate> flowDates(Collection<Long> securityIds, LocalDate after, LocalDate through) {
        var dates = new TreeSet<LocalDate>();
        for (var securityId : securityIds) {
            for (var input : inputsBySecurity.getOrDefault(securityId, List.of())) {
                if (input.action() != Action.BUY && input.action() != Action.SELL) {
                    continue;
                }
                if (input.date().isAfter(after) && !input.date().isAfter(through)) {
                    dates.add(input.date());
                }
            }
        }
        return List.copyOf(dates);
    }

    /**
     * Market value of these positions on {@code date}; {@code null} when a held security has no price on or
     * before that date. With {@code includeFlowsOnDate} false, transactions dated on {@code date} are excluded,
     * which gives the pre-flow value used to close a sub-period.
     */
    private BigDecimal valueAt(Collection<Long> securityIds, LocalDate date, boolean includeFlowsOnDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (var securityId : securityIds) {
            var inputs = inputsBySecurity.getOrDefault(securityId, List.of()).stream()
                    .filter(input -> includeFlowsOnDate ? !input.date().isAfter(date) : input.date().isBefore(date))
                    .toList();
            if (inputs.isEmpty()) {
                continue;
            }
            BigDecimal shares = AcbEngine.summarize(inputs).shareBalance();
            if (shares.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            var prices = pricesBySecurity.get(securityId);
            var price = prices == null ? null : prices.floorEntry(date);
            if (price == null) {
                return null;
            }
            total = total.add(shares.multiply(price.getValue()));
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Net dividends paid in {@code (after, through]} for these securities. */
    private BigDecimal dividendsBetween(Collection<Long> securityIds, LocalDate after, LocalDate through) {
        BigDecimal sum = BigDecimal.ZERO;
        for (var securityId : securityIds) {
            for (var dividend : dividendsBySecurity.getOrDefault(securityId, List.of())) {
                var date = dividend.getPaymentDate();
                if (date.isAfter(after) && !date.isAfter(through)) {
                    sum = sum.add(dividend.getNetAmount());
                }
            }
        }
        return sum;
    }
}
