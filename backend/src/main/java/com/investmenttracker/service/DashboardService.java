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
        var dividends = dividendRepository.findByPortfolioIdOrderByPaymentDateDesc(portfolioId);
        var window = holdingService.performanceWindow(List.of(portfolioId), dividends, today);
        var todaysReturn = todaysReturn(window, today);
        var periodReturns = buildPeriodReturns(window, today);
        var holdingBreakdowns = buildHoldingBreakdowns(window, holdings, today);

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
        var portfolioIds = portfolioRepository.findAllByOrderByNameAsc().stream()
                .map(portfolio -> portfolio.getId())
                .toList();
        var dashboards = portfolioIds.stream().map(this::getDashboard).toList();
        var currentValue = overallCurrentValue(dashboards);
        var invested = dashboards.stream()
                .map(DashboardResponse::invested)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var asOfToday = LocalDate.now();
        var dividends = portfolioIds.stream()
                .map(dividendRepository::findByPortfolioIdOrderByPaymentDateDesc)
                .flatMap(List::stream)
                .toList();
        var window = holdingService.performanceWindow(portfolioIds, dividends, asOfToday);

        return new DashboardResponse(
                currentValue,
                invested,
                dashboards.stream()
                        .map(DashboardResponse::asOfDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(null),
                todaysReturn(window, asOfToday),
                aggregateAllTimeReturn(dashboards, currentValue, invested),
                aggregateInvestedBasedReturn(dashboards, currentValue, invested, DashboardResponse::priceReturn),
                aggregateInvestedBasedReturn(dashboards, currentValue, invested, DashboardResponse::dividendReturn),
                buildPeriodReturns(window, asOfToday),
                aggregateHoldingBreakdowns(dashboards, window, asOfToday),
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
                new PeriodSpec("Today", today.minusDays(1)),
                new PeriodSpec("5 Days", today.minusDays(5)),
                new PeriodSpec("One Month", today.minus(Period.ofMonths(1))),
                new PeriodSpec("Six Month", today.minus(Period.ofMonths(6))),
                new PeriodSpec("One Year", today.minus(Period.ofYears(1)))
        );
    }

    private List<PeriodReturn> buildPeriodReturns(PerformanceWindow window, LocalDate today) {
        var securityIds = window.securityIds();
        return periodSpecs(today).stream()
                .map(spec -> toPeriodReturn(spec.label(), window.compute(securityIds, spec.target(), today)))
                .toList();
    }

    /** Today's figure is the price leg of the one-day window, so an intraday buy is not read as a gain. */
    private ReturnFigure todaysReturn(PerformanceWindow window, LocalDate today) {
        var target = today.minusDays(1);
        var result = window.compute(window.securityIds(), target, today);
        if (!result.available()) {
            return ReturnFigure.unavailable();
        }
        return new ReturnFigure(result.priceAmount(), result.pricePct(), target, true);
    }

    private static PeriodReturn toPeriodReturn(String label, PerformanceWindow.Result result) {
        if (!result.available()) {
            return new PeriodReturn(label, null, null, null, null, null, null, false);
        }
        return new PeriodReturn(
                label,
                result.totalAmount(),
                result.totalPct(),
                result.priceAmount(),
                result.pricePct(),
                result.dividendAmount(),
                result.dividendPct(),
                true
        );
    }

    /**
     * Per-security split of the price and dividend returns so the dashboard rows can expand per ETF.
     * ponytail: recomputes each security's value as-of every period target (one query per period); fine for
     * a personal tracker, switch to a single bulk query if holding/period counts grow.
     */
    private List<HoldingReturnBreakdown> buildHoldingBreakdowns(
            PerformanceWindow window,
            List<HoldingResponse> holdings,
            LocalDate today
    ) {
        if (holdings.isEmpty()) {
            return List.of();
        }
        var specs = periodSpecs(today);
        var dividendsBySecurity = window.dividendsBySecurity();

        var breakdowns = new ArrayList<HoldingReturnBreakdown>();
        for (var holding : holdings) {
            Long securityId = holding.securityId();
            BigDecimal basis = holding.totalAcb();
            var securityDividends = dividendsBySecurity.getOrDefault(securityId, List.of());

            var priceReturn = holdingPriceReturn(holding.marketValue(), basis);
            var dividendReturn = holdingDividendReturn(sumNet(securityDividends, null, null), basis);

            var periodReturns = specs.stream()
                    .map(spec -> toPeriodReturn(
                            spec.label(),
                            window.compute(List.of(securityId), spec.target(), today)))
                    .toList();
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
    private List<HoldingReturnBreakdown> aggregateHoldingBreakdowns(
            List<DashboardResponse> dashboards,
            PerformanceWindow window,
            LocalDate today
    ) {
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

            // Periods are chained across every portfolio holding this security at once, not summed per portfolio.
            var periods = periodSpecs(today).stream()
                    .map(spec -> toPeriodReturn(
                            spec.label(),
                            window.compute(List.of(first.securityId()), spec.target(), today)))
                    .toList();
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

    private BigDecimal pctOf(BigDecimal amount, BigDecimal basis) {
        return amount.multiply(BigDecimal.valueOf(100)).divide(basis, PCT_SCALE, RoundingMode.HALF_UP);
    }

}
