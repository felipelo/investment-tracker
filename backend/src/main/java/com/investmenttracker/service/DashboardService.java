package com.investmenttracker.service;

import com.investmenttracker.domain.PortfolioSnapshot;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.PortfolioSnapshotRepository;
import com.investmenttracker.web.dto.DashboardResponse;
import com.investmenttracker.web.dto.DashboardResponse.AllocationSlice;
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
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;

    private final HoldingService holdingService;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;

    public DashboardService(
            HoldingService holdingService,
            PortfolioSnapshotRepository portfolioSnapshotRepository,
            DividendRepository dividendRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.holdingService = holdingService;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
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
        var allTime = buildAllTimeReturn(metrics, dividendRepository.sumNetByPortfolio(portfolioId));
        var today = LocalDate.now();
        var todaysReturn = computeReturn(portfolioId, currentValue, today.minusDays(1));
        var periodReturns = buildPeriodReturns(portfolioId, currentValue, today);

        return new DashboardResponse(
                currentValue,
                metrics.invested(),
                asOfDate,
                todaysReturn,
                allTime,
                periodReturns,
                allocation
        );
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

    private List<PeriodReturn> buildPeriodReturns(Long portfolioId, BigDecimal currentValue, LocalDate today) {
        return List.of(
                periodReturn(portfolioId, currentValue, "5 Days", today.minusDays(5)),
                periodReturn(portfolioId, currentValue, "One Month", today.minus(Period.ofMonths(1))),
                periodReturn(portfolioId, currentValue, "Six Month", today.minus(Period.ofMonths(6))),
                periodReturn(portfolioId, currentValue, "One Year", today.minus(Period.ofYears(1)))
        );
    }

    private PeriodReturn periodReturn(Long portfolioId, BigDecimal currentValue, String label, LocalDate target) {
        var figure = computeReturn(portfolioId, currentValue, target);
        return new PeriodReturn(label, figure.amount(), figure.pct(), figure.available());
    }

    /** Current value minus the nearest stored portfolio snapshot at or before {@code target}. */
    private ReturnFigure computeReturn(Long portfolioId, BigDecimal currentValue, LocalDate target) {
        if (currentValue == null) {
            return ReturnFigure.unavailable();
        }
        PortfolioSnapshot basis = portfolioSnapshotRepository
                .findTopByPortfolioIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(portfolioId, target)
                .orElse(null);
        if (basis == null) {
            return ReturnFigure.unavailable();
        }
        BigDecimal basisValue = basis.getMarketValue();
        BigDecimal amount = currentValue.subtract(basisValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pct = basisValue.compareTo(BigDecimal.ZERO) != 0
                ? amount.multiply(BigDecimal.valueOf(100)).divide(basisValue, PCT_SCALE, RoundingMode.HALF_UP)
                : null;
        return new ReturnFigure(amount, pct, basis.getSnapshotDate(), true);
    }
}
