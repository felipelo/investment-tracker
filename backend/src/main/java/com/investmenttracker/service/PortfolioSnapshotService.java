package com.investmenttracker.service;

import com.investmenttracker.domain.PortfolioSnapshot;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.PortfolioSnapshotRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.CreatePortfolioSnapshotRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Service
@Transactional
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityTransactionRepository securityTransactionRepository;
    private final HoldingService holdingService;

    public PortfolioSnapshotService(
            PortfolioSnapshotRepository portfolioSnapshotRepository,
            PortfolioRepository portfolioRepository,
            SecurityTransactionRepository securityTransactionRepository,
            HoldingService holdingService
    ) {
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioRepository = portfolioRepository;
        this.securityTransactionRepository = securityTransactionRepository;
        this.holdingService = holdingService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioSnapshot> list(Long portfolioId) {
        requirePortfolio(portfolioId);
        return portfolioSnapshotRepository.findByPortfolioIdOrderBySnapshotDateDesc(portfolioId);
    }

    /** Manual entry: record (or overwrite) the portfolio market value for a given date. */
    public PortfolioSnapshot create(Long portfolioId, CreatePortfolioSnapshotRequest request) {
        var portfolio = requirePortfolio(portfolioId);
        var snapshot = portfolioSnapshotRepository
                .findByPortfolioIdAndSnapshotDate(portfolioId, request.date())
                .orElseGet(PortfolioSnapshot::new);
        snapshot.setPortfolio(portfolio);
        snapshot.setSnapshotDate(request.date());
        snapshot.setMarketValue(request.marketValue());
        return portfolioSnapshotRepository.save(snapshot);
    }

    /**
     * Auto-capture: after prices change, recompute and upsert the current market value
     * (stamped at {@code date}) for every portfolio that holds any of the given securities.
     * Portfolios with no priced market value are skipped.
     */
    public void captureForSecurities(Collection<Long> securityIds, LocalDate date) {
        if (securityIds == null || securityIds.isEmpty()) {
            return;
        }
        for (var portfolioId : securityTransactionRepository.findPortfolioIdsHoldingSecurities(securityIds)) {
            var metrics = holdingService.portfolioMetrics(portfolioId);
            if (metrics.marketValue() == null) {
                continue;
            }
            var snapshot = portfolioSnapshotRepository
                    .findByPortfolioIdAndSnapshotDate(portfolioId, date)
                    .orElseGet(PortfolioSnapshot::new);
            snapshot.setPortfolio(portfolioRepository.getReferenceById(portfolioId));
            snapshot.setSnapshotDate(date);
            snapshot.setMarketValue(metrics.marketValue());
            portfolioSnapshotRepository.save(snapshot);
        }
    }

    private com.investmenttracker.domain.Portfolio requirePortfolio(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio", portfolioId));
    }
}
