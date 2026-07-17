package com.investmenttracker.service;

import com.investmenttracker.domain.Dividend;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.web.dto.DashboardResponse;
import com.investmenttracker.web.dto.DashboardResponse.AllocationSlice;
import com.investmenttracker.web.dto.DashboardResponse.HoldingReturnBreakdown;
import com.investmenttracker.web.dto.DashboardResponse.PeriodReturn;
import com.investmenttracker.web.dto.DashboardResponse.ReturnFigure;
import com.investmenttracker.web.dto.HoldingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;

    private final HoldingService holdingService;
    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;

    public DashboardService(
            HoldingService holdingService,
            DividendRepository dividendRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.holdingService = holdingService;
        this.dividendRepository = dividendRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public DashboardResponse getDashboard(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }

        var metrics = holdingService.portfolioMetrics(portfolioId);
        var holdings = holdingService.listHoldings(portfolioId);
        BigDecimal currentValue = metrics.marketValue();

        var allocation = buildAllocation(holdings);
        var asOfDate = latestPriceDate(holdings);
        BigDecimal netDividends = dividendRepository.sumNetByPortfolio(portfolioId);
        var allTime = buildAllTimeReturn(metrics, netDividends);
        var priceReturn = buildPriceReturn(metrics);
        var dividendReturn = buildDividendReturn(netDividends, metrics.invested());
        var today = LocalDate.now();
        var todaysReturn = computeReturn(portfolioId, currentValue, today.minusDays(1));
        var periodReturns = buildPeriodReturns(portfolioId, currentValue, today);
        var holdingBreakdowns = buildHoldingBreakdowns(portfolioId, holdings, today);

        return new DashboardResponse(
                currentValue,
                metrics.invested(),
                asOfDate,
                todaysReturn,
                allTime,
                priceReturn,
                dividendReturn,
                periodReturns,
                holdingBreakdowns,
                allocation
        );
    }

    public DashboardResponse getOverallDashboard() {
        // ponytail: Reuse authoritative per-portfolio math; switch to bulk queries if portfolio counts grow.
        var dashboards = portfolioRepository.findAllByOrderByNameAsc().stream()
                .map(portfolio -> getDashboard(portfolio.getId()))
                .toList();
        var currentValue = overallCurrentValue(dashboards);
        var invested = dashboards.stream()
                .map(DashboardResponse::invested)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var today = aggregateReturn(dashboards, currentValue, DashboardResponse::todaysReturn);

        var periodReturns = dashboards.isEmpty()
                ? List.<PeriodReturn>of()
                : IntStream.range(0, dashboards.getFirst().periodReturns().size())
                        .mapToObj(index -> aggregatePeriodReturn(dashboards, currentValue, index))
                        .toList();

        return new DashboardResponse(
                currentValue,
                invested,
                dashboards.stream()
                        .map(DashboardResponse::asOfDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(null),
                today,
                aggregateAllTimeReturn(dashboards, currentValue, invested),
                aggregateInvestedBasedReturn(dashboards, currentValue, invested, DashboardResponse::priceReturn),
                aggregateInvestedBasedReturn(dashboards, currentValue, invested, DashboardResponse::dividendReturn),
                periodReturns,
                aggregateHoldingBreakdowns(dashboards),
                aggregateAllocation(dashboards)
        );
    }

    private BigDecimal overallCurrentValue(List<DashboardResponse> dashboards) {
        BigDecimal total = null;
        for (var dashboard : dashboards) {
            if (dashboard.portfolioValue() == null) {
                if (dashboard.invested().compareTo(BigDecimal.ZERO) != 0) {
                    return null;
                }
                continue;
            }
            total = total == null ? dashboard.portfolioValue() : total.add(dashboard.portfolioValue());
        }
        return total;
    }

    private ReturnFigure aggregateReturn(
            List<DashboardResponse> dashboards,
            BigDecimal currentValue,
            Function<DashboardResponse, ReturnFigure> figureProvider
    ) {
        if (currentValue == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal basis = BigDecimal.ZERO;
        LocalDate basisDate = null;
        boolean mixedBasisDates = false;

        for (var dashboard : dashboards) {
            if (dashboard.portfolioValue() == null && dashboard.invested().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            var figure = figureProvider.apply(dashboard);
            if (!figure.available() || figure.amount() == null || dashboard.portfolioValue() == null) {
                return ReturnFigure.unavailable();
            }
            amount = amount.add(figure.amount());
            basis = basis.add(dashboard.portfolioValue().subtract(figure.amount()));
            if (basisDate == null) {
                basisDate = figure.basisDate();
            } else if (!Objects.equals(basisDate, figure.basisDate())) {
                mixedBasisDates = true;
            }
        }

        var pct = basis.compareTo(BigDecimal.ZERO) == 0
                ? null
                : amount.multiply(BigDecimal.valueOf(100)).divide(basis, PCT_SCALE, RoundingMode.HALF_UP);
        return new ReturnFigure(
                amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                pct,
                mixedBasisDates ? null : basisDate,
                true
        );
    }

    private ReturnFigure aggregateAllTimeReturn(
            List<DashboardResponse> dashboards,
            BigDecimal currentValue,
            BigDecimal invested
    ) {
        return aggregateInvestedBasedReturn(dashboards, currentValue, invested, DashboardResponse::allTimeReturn);
    }

    /** Sums a per-portfolio figure and expresses the total as a percent of combined invested cost. */
    private ReturnFigure aggregateInvestedBasedReturn(
            List<DashboardResponse> dashboards,
            BigDecimal currentValue,
            BigDecimal invested,
            Function<DashboardResponse, ReturnFigure> figureProvider
    ) {
        if (currentValue == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal amount = BigDecimal.ZERO;
        for (var dashboard : dashboards) {
            if (dashboard.portfolioValue() == null && dashboard.invested().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            var figure = figureProvider.apply(dashboard);
            if (!figure.available() || figure.amount() == null) {
                return ReturnFigure.unavailable();
            }
            amount = amount.add(figure.amount());
        }
        var pct = invested.compareTo(BigDecimal.ZERO) == 0
                ? null
                : amount.multiply(BigDecimal.valueOf(100)).divide(invested, PCT_SCALE, RoundingMode.HALF_UP);
        return new ReturnFigure(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP), pct, null, true);
    }

    private PeriodReturn aggregatePeriodReturn(
            List<DashboardResponse> dashboards,
            BigDecimal currentValue,
            int index
    ) {
        var label = dashboards.getFirst().periodReturns().get(index).label();
        if (currentValue == null) {
            return new PeriodReturn(label, null, null, null, null, null, null, false);
        }
        BigDecimal priceSum = BigDecimal.ZERO;
        BigDecimal dividendSum = BigDecimal.ZERO;
        BigDecimal basisSum = BigDecimal.ZERO;
        for (var dashboard : dashboards) {
            if (dashboard.portfolioValue() == null && dashboard.invested().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            var period = dashboard.periodReturns().get(index);
            if (!period.available() || period.priceAmount() == null || dashboard.portfolioValue() == null) {
                return new PeriodReturn(label, null, null, null, null, null, null, false);
            }
            priceSum = priceSum.add(period.priceAmount());
            dividendSum = dividendSum.add(period.dividendAmount());
            basisSum = basisSum.add(dashboard.portfolioValue().subtract(period.priceAmount()));
        }
        BigDecimal total = priceSum.add(dividendSum);
        BigDecimal pct = basisSum.compareTo(BigDecimal.ZERO) == 0
                ? null
                : total.multiply(BigDecimal.valueOf(100)).divide(basisSum, PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal pricePct = basisSum.compareTo(BigDecimal.ZERO) == 0
                ? null
                : priceSum.multiply(BigDecimal.valueOf(100)).divide(basisSum, PCT_SCALE, RoundingMode.HALF_UP);
        BigDecimal dividendPct = basisSum.compareTo(BigDecimal.ZERO) == 0
                ? null
                : dividendSum.multiply(BigDecimal.valueOf(100)).divide(basisSum, PCT_SCALE, RoundingMode.HALF_UP);
        return new PeriodReturn(
                label,
                total.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                pct,
                priceSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                pricePct,
                dividendSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                dividendPct,
                true
        );
    }

    private List<AllocationSlice> aggregateAllocation(List<DashboardResponse> dashboards) {
        var totals = new LinkedHashMap<Long, AllocationSlice>();
        for (var dashboard : dashboards) {
            for (var slice : dashboard.allocation()) {
                var existing = totals.get(slice.securityId());
                var marketValue = existing == null
                        ? slice.marketValue()
                        : existing.marketValue().add(slice.marketValue());
                totals.put(
                        slice.securityId(),
                        new AllocationSlice(slice.securityId(), slice.ticker(), slice.name(), marketValue, BigDecimal.ZERO)
                );
            }
        }
        var total = totals.values().stream()
                .map(AllocationSlice::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }
        return totals.values().stream()
                .map(slice -> new AllocationSlice(
                        slice.securityId(),
                        slice.ticker(),
                        slice.name(),
                        slice.marketValue(),
                        slice.marketValue()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(total, PCT_SCALE, RoundingMode.HALF_UP)
                ))
                .sorted(Comparator.comparing(AllocationSlice::ticker))
                .toList();
    }

    private List<AllocationSlice> buildAllocation(List<HoldingResponse> holdings) {
        BigDecimal total = BigDecimal.ZERO;
        for (var holding : holdings) {
            if (holding.marketValue() != null) {
                total = total.add(holding.marketValue());
            }
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        var slices = new ArrayList<AllocationSlice>();
        for (var holding : holdings) {
            if (holding.marketValue() == null) {
                continue;
            }
            BigDecimal pct = holding.marketValue()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, PCT_SCALE, RoundingMode.HALF_UP);
            slices.add(new AllocationSlice(
                    holding.securityId(),
                    holding.ticker(),
                    holding.name(),
                    holding.marketValue(),
                    pct
            ));
        }
        return slices;
    }

    private LocalDate latestPriceDate(List<HoldingResponse> holdings) {
        LocalDate latest = null;
        for (var holding : holdings) {
            var priceDate = holding.priceDate();
            if (priceDate != null && (latest == null || priceDate.isAfter(latest))) {
                latest = priceDate;
            }
        }
        return latest;
    }

    private ReturnFigure buildAllTimeReturn(PortfolioMetrics metrics, BigDecimal netDividends) {
        if (metrics.returnAmount() == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal amount = metrics.returnAmount()
                .add(netDividends != null ? netDividends : BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = metrics.invested().compareTo(BigDecimal.ZERO) != 0
                ? amount.multiply(BigDecimal.valueOf(100)).divide(metrics.invested(), PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new ReturnFigure(amount, pct, null, true);
    }

    /** All-time price (capital) return: market value minus invested cost. */
    private ReturnFigure buildPriceReturn(PortfolioMetrics metrics) {
        if (metrics.returnAmount() == null) {
            return ReturnFigure.unavailable();
        }
        return new ReturnFigure(
                metrics.returnAmount().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                metrics.returnPct(),
                null,
                true
        );
    }

    /** All-time dividend return: total net dividends, as a percent of invested cost. */
    private ReturnFigure buildDividendReturn(BigDecimal netDividends, BigDecimal invested) {
        BigDecimal amount = (netDividends != null ? netDividends : BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = invested.compareTo(BigDecimal.ZERO) != 0
                ? amount.multiply(BigDecimal.valueOf(100)).divide(invested, PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new ReturnFigure(amount, pct, null, true);
    }

    private record PeriodSpec(String label, LocalDate target) {
    }

    private List<PeriodSpec> periodSpecs(LocalDate today) {
        return List.of(
                new PeriodSpec("5 Days", today.minusDays(5)),
                new PeriodSpec("One Month", today.minus(Period.ofMonths(1))),
                new PeriodSpec("Six Month", today.minus(Period.ofMonths(6))),
                new PeriodSpec("One Year", today.minus(Period.ofYears(1)))
        );
    }

    private List<PeriodReturn> buildPeriodReturns(Long portfolioId, BigDecimal currentValue, LocalDate today) {
        return periodSpecs(today).stream()
                .map(spec -> periodReturn(portfolioId, currentValue, today, spec.label(), spec.target()))
                .toList();
    }

    /**
     * Per-security split of the price and dividend returns so the dashboard rows can expand per ETF.
     * ponytail: recomputes each security's value as-of every period target (one query per period); fine for
     * a personal tracker, switch to a single bulk query if holding/period counts grow.
     */
    private List<HoldingReturnBreakdown> buildHoldingBreakdowns(
            Long portfolioId,
            List<HoldingResponse> holdings,
            LocalDate today
    ) {
        if (holdings.isEmpty()) {
            return List.of();
        }
        var specs = periodSpecs(today);
        var valuesByPeriod = specs.stream()
                .map(spec -> holdingService.holdingValuesAsOf(portfolioId, spec.target()))
                .toList();
        var dividendsBySecurity = dividendRepository.findByPortfolioIdOrderByPaymentDateDesc(portfolioId).stream()
                .collect(Collectors.groupingBy(dividend -> dividend.getSecurity().getId()));

        var breakdowns = new ArrayList<HoldingReturnBreakdown>();
        for (var holding : holdings) {
            Long securityId = holding.securityId();
            BigDecimal currentValue = holding.marketValue();
            BigDecimal basis = holding.totalAcb();
            var securityDividends = dividendsBySecurity.getOrDefault(securityId, List.of());

            var priceReturn = holdingPriceReturn(currentValue, basis);
            var dividendReturn = holdingDividendReturn(sumNet(securityDividends, null, null), basis);

            var periodReturns = new ArrayList<PeriodReturn>();
            for (int i = 0; i < specs.size(); i++) {
                var spec = specs.get(i);
                var values = valuesByPeriod.get(i);
                BigDecimal basisValue = values == null ? null : values.get(securityId);
                periodReturns.add(holdingPeriodReturn(spec.label(), currentValue, basisValue, spec.target(), today, securityDividends));
            }
            breakdowns.add(new HoldingReturnBreakdown(
                    securityId, holding.ticker(), holding.name(), priceReturn, dividendReturn, periodReturns));
        }
        return breakdowns;
    }

    /** All-time price return for a single holding: current market value minus its ACB. */
    private ReturnFigure holdingPriceReturn(BigDecimal currentValue, BigDecimal basis) {
        if (currentValue == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal amount = currentValue.subtract(basis).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = basis.compareTo(BigDecimal.ZERO) != 0 ? pctOf(amount, basis) : null;
        return new ReturnFigure(amount, pct, null, true);
    }

    /** All-time dividend return for a single holding: net dividends as a percent of its ACB. */
    private ReturnFigure holdingDividendReturn(BigDecimal netDividends, BigDecimal basis) {
        BigDecimal amount = netDividends.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = basis.compareTo(BigDecimal.ZERO) != 0 ? pctOf(amount, basis) : null;
        return new ReturnFigure(amount, pct, null, true);
    }

    private PeriodReturn holdingPeriodReturn(
            String label,
            BigDecimal currentValue,
            BigDecimal basisValue,
            LocalDate target,
            LocalDate today,
            List<Dividend> securityDividends
    ) {
        if (currentValue == null || basisValue == null) {
            return new PeriodReturn(label, null, null, null, null, null, null, false);
        }
        BigDecimal priceAmount = currentValue.subtract(basisValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal dividendAmount = sumNet(securityDividends, target, today).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = priceAmount.add(dividendAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean hasBasis = basisValue.compareTo(BigDecimal.ZERO) != 0;
        BigDecimal pricePct = hasBasis ? pctOf(priceAmount, basisValue) : null;
        BigDecimal dividendPct = hasBasis ? pctOf(dividendAmount, basisValue) : null;
        BigDecimal pct = hasBasis ? pctOf(total, basisValue) : null;
        return new PeriodReturn(label, total, pct, priceAmount, pricePct, dividendAmount, dividendPct, true);
    }

    /** Net dividends ({@code gross - withholding}) with payment date in {@code (after, through]}; null bounds mean unbounded. */
    private static BigDecimal sumNet(List<Dividend> dividends, LocalDate after, LocalDate through) {
        BigDecimal sum = BigDecimal.ZERO;
        for (var dividend : dividends) {
            var date = dividend.getPaymentDate();
            if (after != null && !date.isAfter(after)) {
                continue;
            }
            if (through != null && date.isAfter(through)) {
                continue;
            }
            sum = sum.add(dividend.getNetAmount());
        }
        return sum;
    }

    /**
     * Aggregates per-portfolio holding breakdowns for the overall dashboard by summing each security's
     * amounts and rebuilding percentages against a basis reconstructed from each contributor.
     * ponytail: a security whose return is exactly zero in a portfolio yields no reconstructable basis for
     * that leg, so its aggregate percent is suppressed rather than approximated. Switch to carrying an
     * explicit basis if exact overall per-ETF percents on zero-return legs are needed.
     */
    private List<HoldingReturnBreakdown> aggregateHoldingBreakdowns(List<DashboardResponse> dashboards) {
        var bySecurity = new LinkedHashMap<Long, List<HoldingReturnBreakdown>>();
        for (var dashboard : dashboards) {
            for (var breakdown : dashboard.holdingBreakdowns()) {
                bySecurity.computeIfAbsent(breakdown.securityId(), key -> new ArrayList<>()).add(breakdown);
            }
        }

        var result = new ArrayList<HoldingReturnBreakdown>();
        for (var group : bySecurity.values()) {
            var first = group.getFirst();
            var priceReturn = aggregateReconstructed(group.stream().map(HoldingReturnBreakdown::priceReturn).toList());
            var dividendReturn = aggregateReconstructed(group.stream().map(HoldingReturnBreakdown::dividendReturn).toList());

            var periods = new ArrayList<PeriodReturn>();
            for (int index = 0; index < first.periodReturns().size(); index++) {
                final int periodIndex = index;
                var label = first.periodReturns().get(periodIndex).label();
                var contributors = group.stream().map(breakdown -> breakdown.periodReturns().get(periodIndex)).toList();
                periods.add(aggregateHoldingPeriod(label, contributors));
            }
            result.add(new HoldingReturnBreakdown(
                    first.securityId(), first.ticker(), first.name(), priceReturn, dividendReturn, periods));
        }
        result.sort(Comparator.comparing(HoldingReturnBreakdown::ticker));
        return result;
    }

    private ReturnFigure aggregateReconstructed(List<ReturnFigure> figures) {
        BigDecimal amountSum = BigDecimal.ZERO;
        BigDecimal basisSum = BigDecimal.ZERO;
        boolean basisKnown = true;
        boolean any = false;
        for (var figure : figures) {
            if (figure == null || !figure.available() || figure.amount() == null) {
                continue;
            }
            any = true;
            amountSum = amountSum.add(figure.amount());
            BigDecimal basis = reconstructBasis(figure.amount(), figure.pct());
            if (basis == null) {
                basisKnown = false;
            } else {
                basisSum = basisSum.add(basis);
            }
        }
        if (!any) {
            return ReturnFigure.unavailable();
        }
        BigDecimal pct = (basisKnown && basisSum.compareTo(BigDecimal.ZERO) != 0) ? pctOf(amountSum, basisSum) : null;
        return new ReturnFigure(amountSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP), pct, null, true);
    }

    private PeriodReturn aggregateHoldingPeriod(String label, List<PeriodReturn> contributors) {
        BigDecimal priceSum = BigDecimal.ZERO;
        BigDecimal dividendSum = BigDecimal.ZERO;
        BigDecimal basisSum = BigDecimal.ZERO;
        boolean basisKnown = true;
        boolean any = false;
        for (var period : contributors) {
            if (period == null || !period.available() || period.priceAmount() == null) {
                continue;
            }
            any = true;
            priceSum = priceSum.add(period.priceAmount());
            dividendSum = dividendSum.add(period.dividendAmount());
            BigDecimal basis = reconstructPeriodBasis(period);
            if (basis == null) {
                basisKnown = false;
            } else {
                basisSum = basisSum.add(basis);
            }
        }
        if (!any) {
            return new PeriodReturn(label, null, null, null, null, null, null, false);
        }
        BigDecimal total = priceSum.add(dividendSum);
        boolean hasBasis = basisKnown && basisSum.compareTo(BigDecimal.ZERO) != 0;
        return new PeriodReturn(
                label,
                total.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                hasBasis ? pctOf(total, basisSum) : null,
                priceSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                hasBasis ? pctOf(priceSum, basisSum) : null,
                dividendSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                hasBasis ? pctOf(dividendSum, basisSum) : null,
                true
        );
    }

    /** Recovers the cost basis behind a figure via {@code amount * 100 / pct}; null when it cannot be recovered. */
    private static BigDecimal reconstructBasis(BigDecimal amount, BigDecimal pct) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if (pct == null || pct.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(pct, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Period basis is shared by its price and dividend legs; recover it from whichever leg has a usable percent. */
    private static BigDecimal reconstructPeriodBasis(PeriodReturn period) {
        BigDecimal fromPrice = reconstructBasis(period.priceAmount(), period.pricePct());
        if (fromPrice == null || fromPrice.compareTo(BigDecimal.ZERO) != 0) {
            return fromPrice;
        }
        BigDecimal fromDividend = reconstructBasis(period.dividendAmount(), period.dividendPct());
        return fromDividend != null ? fromDividend : fromPrice;
    }

    private BigDecimal pctOf(BigDecimal amount, BigDecimal basis) {
        return amount.multiply(BigDecimal.valueOf(100)).divide(basis, PCT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Splits a period return into price (current value minus the basis snapshot) and dividends
     * (net dividends paid after that snapshot date through today). Total = price + dividends,
     * with the percent taken against the snapshot value.
     */
    private PeriodReturn periodReturn(
            Long portfolioId,
            BigDecimal currentValue,
            LocalDate today,
            String label,
            LocalDate target
    ) {
        var price = computeReturn(portfolioId, currentValue, target);
        if (!price.available()) {
            return new PeriodReturn(label, null, null, null, null, null, null, false);
        }
        BigDecimal priceAmount = price.amount();
        BigDecimal pricePct = price.pct();
        BigDecimal snapshotValue = currentValue.subtract(priceAmount);
        BigDecimal dividendAmount = dividendRepository
                .sumNetByPortfolioBetween(portfolioId, price.basisDate(), today)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal total = priceAmount.add(dividendAmount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = snapshotValue.compareTo(BigDecimal.ZERO) != 0
                ? total.multiply(BigDecimal.valueOf(100)).divide(snapshotValue, PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal dividendPct = snapshotValue.compareTo(BigDecimal.ZERO) != 0
                ? dividendAmount.multiply(BigDecimal.valueOf(100)).divide(snapshotValue, PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new PeriodReturn(label, total, pct, priceAmount, pricePct, dividendAmount, dividendPct, true);
    }

    /** Current value minus the live-computed portfolio value as of {@code target}. */
    private ReturnFigure computeReturn(Long portfolioId, BigDecimal currentValue, LocalDate target) {
        if (currentValue == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal basisValue = holdingService.portfolioValueAsOf(portfolioId, target);
        if (basisValue == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal amount = currentValue.subtract(basisValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = basisValue.compareTo(BigDecimal.ZERO) != 0
                ? amount.multiply(BigDecimal.valueOf(100)).divide(basisValue, PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new ReturnFigure(amount, pct, target, true);
    }
}
