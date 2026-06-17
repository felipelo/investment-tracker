package com.investmenttracker.service;

import com.investmenttracker.acb.AcbEngine;
import com.investmenttracker.acb.HoldingSummary;
import com.investmenttracker.acb.SecurityTransactionInput;
import com.investmenttracker.acb.SecurityTransactionInputs;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.PriceSnapshotRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.HoldingHistoryRowResponse;
import com.investmenttracker.web.dto.HoldingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HoldingService {

    private final SecurityTransactionRepository securityTransactionRepository;
    private final SecurityRepository securityRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public HoldingService(
            SecurityTransactionRepository securityTransactionRepository,
            SecurityRepository securityRepository,
            PriceSnapshotRepository priceSnapshotRepository
    ) {
        this.securityTransactionRepository = securityTransactionRepository;
        this.securityRepository = securityRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    public List<HoldingResponse> listHoldings(Long portfolioId) {
        var transactions = securityTransactionRepository.findAllForHoldingsByPortfolio(portfolioId);
        if (transactions.isEmpty()) {
            return List.of();
        }

        var latestPriceBySecurity = latestPriceBySecurity();

        var holdings = new ArrayList<HoldingResponse>();
        for (var securityTransactions : groupBySecurity(transactions).values()) {
            var summary = AcbEngine.summarize(toInputs(securityTransactions));
            if (summary.shareBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            var security = securityTransactions.getFirst().getSecurity();
            PriceSnapshot latestPrice = latestPriceBySecurity.get(security.getId());
            holdings.add(HoldingResponse.from(security, summary, latestPrice));
        }

        holdings.sort(Comparator.comparing(HoldingResponse::ticker));
        return List.copyOf(holdings);
    }

    public PortfolioMetrics portfolioMetrics(Long portfolioId) {
        var transactions = securityTransactionRepository.findAllForHoldingsByPortfolio(portfolioId);
        if (transactions.isEmpty()) {
            return new PortfolioMetrics(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), null, null, null, 0);
        }

        var latestPriceBySecurity = latestPriceBySecurity();

        BigDecimal invested = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal marketValue = null;
        int holdingsCount = 0;

        for (var securityTransactions : groupBySecurity(transactions).values()) {
            var summary = AcbEngine.summarize(toInputs(securityTransactions));
            invested = invested.add(summary.investedCost());

            if (summary.shareBalance().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            holdingsCount++;

            var security = securityTransactions.getFirst().getSecurity();
            PriceSnapshot latestPrice = latestPriceBySecurity.get(security.getId());
            if (latestPrice != null) {
                BigDecimal value = summary.shareBalance()
                        .multiply(latestPrice.getPrice())
                        .setScale(4, RoundingMode.HALF_UP);
                marketValue = marketValue == null ? value : marketValue.add(value);
            }
        }

        BigDecimal returnAmount = marketValue != null ? marketValue.subtract(invested) : null;
        BigDecimal returnPct = (returnAmount != null && invested.compareTo(BigDecimal.ZERO) != 0)
                ? returnAmount.multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP)
                : null;

        return new PortfolioMetrics(invested, marketValue, returnAmount, returnPct, holdingsCount);
    }

    public List<HoldingHistoryRowResponse> getHistory(Long portfolioId, Long securityId) {
        if (!securityRepository.existsById(securityId)) {
            throw new NotFoundException("Security", securityId);
        }

        var transactions = securityTransactionRepository
                .findBySecurityIdAndPortfolioForHoldings(securityId, portfolioId);
        return AcbEngine.compute(toInputs(transactions)).stream()
                .map(HoldingHistoryRowResponse::from)
                .toList();
    }

    private java.util.Map<Long, PriceSnapshot> latestPriceBySecurity() {
        return priceSnapshotRepository.findLatestPerSecurity().stream()
                .collect(Collectors.toMap(
                        snapshot -> snapshot.getSecurity().getId(),
                        Function.identity()
                ));
    }

    private static java.util.Map<Long, List<SecurityTransaction>> groupBySecurity(
            List<SecurityTransaction> transactions
    ) {
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        transaction -> transaction.getSecurity().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private static List<SecurityTransactionInput> toInputs(
            List<SecurityTransaction> transactions
    ) {
        return transactions.stream()
                .map(SecurityTransactionInputs::from)
                .toList();
    }
}
