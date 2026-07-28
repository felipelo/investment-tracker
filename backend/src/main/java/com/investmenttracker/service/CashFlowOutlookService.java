package com.investmenttracker.service;

import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.web.dto.CashFlowOutlookResponse;
import com.investmenttracker.web.dto.CashFlowOutlookResponse.CashFlowEvent;
import com.investmenttracker.web.dto.CashFlowOutlookResponse.WeekBucket;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;

/**
 * Read-only cash flow projection: upcoming dividends against upcoming fees and interest, both
 * inferred from the rhythm of what has already been recorded. The running total starts at zero, so
 * the question it answers is "do the dividends cover the bills", not "will my balance go negative".
 *
 * <p>Every event is an estimate. Nothing here is a confirmed charge or a broker feed.
 */
@Service
@Transactional(readOnly = true)
public class CashFlowOutlookService {

    private static final int MONEY_SCALE = 4;
    private static final int HORIZON_WEEKS = 8;
    private static final int OUTFLOW_LOOKBACK_MONTHS = 12;
    /** 18 months so a quarterly payer still yields enough payments to infer a cadence. */
    private static final int INFLOW_LOOKBACK_MONTHS = 18;
    private static final List<Integer> CADENCE_MONTHS = List.of(1, 3, 6, 12);
    private static final double DAYS_PER_MONTH = 30.4375;

    private final CashTransactionRepository cashTransactionRepository;
    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;

    public CashFlowOutlookService(
            CashTransactionRepository cashTransactionRepository,
            DividendRepository dividendRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.cashTransactionRepository = cashTransactionRepository;
        this.dividendRepository = dividendRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /** @param portfolioId scopes the outlook; {@code null} spans every portfolio. */
    public CashFlowOutlookResponse getOutlook(Long portfolioId) {
        if (portfolioId != null && !portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }

        var today = LocalDate.now();
        var firstWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var horizonEnd = firstWeekStart.plusWeeks(HORIZON_WEEKS).minusDays(1);

        var warnings = new LinkedHashSet<String>();
        var projected = new ArrayList<Projection>();
        projected.addAll(projectOutflows(portfolioId, today, horizonEnd));
        projected.addAll(projectInflows(portfolioId, today, horizonEnd, warnings));
        projected.sort(Comparator.comparing(Projection::date).thenComparing(Projection::label));

        var events = new ArrayList<CashFlowEvent>(projected.size());
        BigDecimal runningTotal = BigDecimal.ZERO;
        BigDecimal moneyIn = BigDecimal.ZERO;
        BigDecimal moneyOut = BigDecimal.ZERO;
        BigDecimal lowest = null;
        LocalDate lowestOn = null;
        for (int i = 0; i < projected.size(); i++) {
            var p = projected.get(i);
            runningTotal = runningTotal.add(p.amount());
            if (p.amount().signum() >= 0) {
                moneyIn = moneyIn.add(p.amount());
            } else {
                moneyOut = moneyOut.add(p.amount().negate());
            }
            // The tightest point is measured at the end of a day, never between two events on the
            // same day: everything dated the same day settles that day, and the order within it is
            // just the sort, so an intra-day dip would be a shortfall the user never actually sees.
            boolean endOfDay = i + 1 == projected.size() || !projected.get(i + 1).date().equals(p.date());
            if (endOfDay && (lowest == null || runningTotal.compareTo(lowest) < 0)) {
                lowest = runningTotal;
                lowestOn = p.date();
            }
            events.add(new CashFlowEvent(p.date(), p.label(), p.category(), money(p.amount()), money(runningTotal)));
        }

        return new CashFlowOutlookResponse(
                today,
                horizonEnd,
                money(moneyIn),
                money(moneyOut),
                money(moneyIn.subtract(moneyOut)),
                money(lowest == null ? BigDecimal.ZERO : lowest),
                lowestOn,
                buildWeeks(firstWeekStart, projected),
                events,
                List.copyOf(warnings)
        );
    }

    /**
     * Fees and interest, projected from the most recent month in which each account recorded any.
     * Withdrawals never appear: those are the user funding the payment, not the payment itself.
     */
    private List<Projection> projectOutflows(Long portfolioId, LocalDate today, LocalDate horizonEnd) {
        // ponytail: only history strictly before today is read, so every event is a projection. A
        // fee you record with a future date will not show up as a confirmed event. Upgrade path:
        // read forward-dated rows as actuals and suppress the projection that would collide.
        var history = cashTransactionRepository
                .findFeeAndInterestSince(portfolioId, today.minusMonths(OUTFLOW_LOOKBACK_MONTHS)).stream()
                .filter(tx -> tx.getDate().isBefore(today))
                .toList();

        var baselineMonths = new LinkedHashMap<Long, YearMonth>();
        for (var tx : history) {
            baselineMonths.merge(
                    tx.getAccount().getId(),
                    YearMonth.from(tx.getDate()),
                    (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing
            );
        }

        var projections = new ArrayList<Projection>();
        for (var entry : baselineMonths.entrySet()) {
            var baseline = history.stream()
                    .filter(tx -> tx.getAccount().getId().equals(entry.getKey()))
                    .filter(tx -> YearMonth.from(tx.getDate()).equals(entry.getValue()))
                    .toList();
            if (baseline.isEmpty()) {
                continue;
            }
            var accountLabel = baseline.getFirst().getAccount().getLabel();
            // Two fees booked on one day are one charge as far as the schedule is concerned; they
            // would otherwise project as two rows with the same date and the same label.
            var perDay = new LinkedHashMap<Charge, BigDecimal>();
            for (var tx : keepOneSideOfInterest(baseline)) {
                perDay.merge(new Charge(tx.getType(), tx.getDate()), tx.getAmount().abs(), BigDecimal::add);
            }
            for (var charge : perDay.entrySet()) {
                boolean isFee = charge.getKey().type() == CashTransactionType.FEE;
                var label = accountLabel + (isFee ? " fee" : " interest");
                for (var date : repeatMonthly(charge.getKey().date(), today, horizonEnd)) {
                    projections.add(new Projection(date, label, isFee ? "FEE" : "INTEREST",
                            charge.getValue().negate()));
                }
            }
        }
        return projections;
    }

    /**
     * An interest charge and the payment that settles it are two sides of one obligation, so summing
     * both would double the outflow. Within a baseline month, charges win; payments are the fallback
     * for users who only book the payment. Fees are unaffected.
     */
    private static List<CashTransaction> keepOneSideOfInterest(List<CashTransaction> baseline) {
        boolean hasCharge = baseline.stream().anyMatch(tx -> tx.getType() == CashTransactionType.INTEREST_CHARGE);
        return baseline.stream()
                .filter(tx -> tx.getType() != CashTransactionType.INTEREST_PAYMENT || !hasCharge)
                .toList();
    }

    /**
     * Dividends, projected per holding from the cadence of its recorded payment dates. Grouping is
     * per portfolio as well as per security: the same holding in two portfolios pays twice, and
     * merging them would collapse the gaps between dates to zero and wreck the inferred cadence.
     */
    private List<Projection> projectInflows(
            Long portfolioId,
            LocalDate today,
            LocalDate horizonEnd,
            LinkedHashSet<String> warnings
    ) {
        var history = dividendRepository
                .findSince(portfolioId, today.minusMonths(INFLOW_LOOKBACK_MONTHS)).stream()
                .filter(d -> d.getPaymentDate().isBefore(today))
                .toList();

        // Payments landing on one date are one event, even when recorded per account.
        var byHolding = new LinkedHashMap<String, TreeMap<LocalDate, BigDecimal>>();
        var tickers = new LinkedHashMap<String, String>();
        for (var dividend : history) {
            var key = dividend.getPortfolio().getId() + "|" + dividend.getSecurity().getId();
            tickers.putIfAbsent(key, dividend.getSecurity().getTicker());
            byHolding.computeIfAbsent(key, k -> new TreeMap<>())
                    .merge(dividend.getPaymentDate(), dividend.getNetAmount(), BigDecimal::add);
        }

        var projections = new ArrayList<Projection>();
        for (var entry : byHolding.entrySet()) {
            var payments = entry.getValue();
            var ticker = tickers.get(entry.getKey());
            if (payments.size() < 2) {
                warnings.add(ticker + " has only one recorded payment, so it is left out.");
                continue;
            }
            int cadenceMonths = snapToCadence(medianGapDays(List.copyOf(payments.navigableKeySet())));
            var anchor = payments.lastKey();
            // ponytail: the amount is the last payment's net, so it ignores share-count changes -
            // buying more units keeps the estimate low until the next real payment is recorded.
            var amount = payments.get(anchor);
            for (var date : repeatEvery(anchor, cadenceMonths, today, horizonEnd)) {
                projections.add(new Projection(date, ticker + " dividend", "DIVIDEND", amount));
            }
        }
        return projections;
    }

    /** Same day of month as the baseline, clamped on short months. */
    private static List<LocalDate> repeatMonthly(LocalDate anchor, LocalDate today, LocalDate horizonEnd) {
        var dates = new ArrayList<LocalDate>();
        int day = anchor.getDayOfMonth();
        var month = YearMonth.from(anchor);
        while (true) {
            month = month.plusMonths(1);
            var date = month.atDay(Math.min(day, month.lengthOfMonth()));
            if (date.isAfter(horizonEnd)) {
                return dates;
            }
            if (!date.isBefore(today)) {
                dates.add(date);
            }
        }
    }

    /**
     * Steps are counted from the anchor rather than from the previous step, so a month-end payer
     * stays on month end instead of walking backwards through February.
     */
    private static List<LocalDate> repeatEvery(
            LocalDate anchor,
            int cadenceMonths,
            LocalDate today,
            LocalDate horizonEnd
    ) {
        var dates = new ArrayList<LocalDate>();
        for (int step = 1; ; step++) {
            var date = anchor.plusMonths((long) step * cadenceMonths);
            if (date.isAfter(horizonEnd)) {
                return dates;
            }
            if (!date.isBefore(today)) {
                dates.add(date);
            }
        }
    }

    private static long medianGapDays(List<LocalDate> dates) {
        var gaps = new ArrayList<Long>(dates.size() - 1);
        for (int i = 1; i < dates.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        Collections.sort(gaps);
        int middle = gaps.size() / 2;
        return gaps.size() % 2 == 1
                ? gaps.get(middle)
                : (gaps.get(middle - 1) + gaps.get(middle)) / 2;
    }

    /** Nearest of monthly, quarterly, semi-annual, annual. */
    private static int snapToCadence(long medianGapDays) {
        int best = CADENCE_MONTHS.getFirst();
        double bestDistance = Double.MAX_VALUE;
        for (int months : CADENCE_MONTHS) {
            double distance = Math.abs(medianGapDays - months * DAYS_PER_MONTH);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = months;
            }
        }
        return best;
    }

    /** One bucket per week of the horizon, carrying the running total across quiet weeks. */
    private static List<WeekBucket> buildWeeks(LocalDate firstWeekStart, List<Projection> projected) {
        var weeks = new ArrayList<WeekBucket>(HORIZON_WEEKS);
        BigDecimal runningTotal = BigDecimal.ZERO;
        for (int week = 0; week < HORIZON_WEEKS; week++) {
            var weekStart = firstWeekStart.plusWeeks(week);
            var weekEnd = weekStart.plusDays(6);
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (var p : projected) {
                if (p.date().isBefore(weekStart) || p.date().isAfter(weekEnd)) {
                    continue;
                }
                if (p.amount().signum() >= 0) {
                    in = in.add(p.amount());
                } else {
                    out = out.add(p.amount().negate());
                }
            }
            runningTotal = runningTotal.add(in).subtract(out);
            weeks.add(new WeekBucket(weekStart, money(in), money(out), money(runningTotal)));
        }
        return weeks;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** {@code amount} is signed: positive lands as cash, negative leaves. */
    private record Projection(LocalDate date, String label, String category, BigDecimal amount) {
    }

    /** One account's fee or interest on one day, used to merge same-day baseline rows. */
    private record Charge(CashTransactionType type, LocalDate date) {
    }
}
