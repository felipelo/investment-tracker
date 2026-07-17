package com.investmenttracker.service;

import com.investmenttracker.acb.AcbEngine;
import com.investmenttracker.acb.ComputedTransactionRow;
import com.investmenttracker.acb.SecurityTransactionInput;
import com.investmenttracker.acb.SecurityTransactionInputs;
import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Security;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.InterestEntryResponse;
import com.investmenttracker.web.dto.TaxSummaryResponse;
import com.investmenttracker.web.dto.TaxSummaryResponse.DividendIncome;
import com.investmenttracker.web.dto.TaxSummaryResponse.DividendRow;
import com.investmenttracker.web.dto.TaxSummaryResponse.InterestMonthRow;
import com.investmenttracker.web.dto.TaxSummaryResponse.InterestSummary;
import com.investmenttracker.web.dto.TaxSummaryResponse.RealizedGainRow;
import com.investmenttracker.web.dto.TaxSummaryResponse.RealizedGains;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.Year;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Read-only per-tax-year aggregation (REQUIREMENTS.md section 6.6): realized capital
 * gains by security, dividend income by security, and the Smith Maneuver interest
 * summary by month. A record-keeping aid, not tax advice (REQUIREMENTS.md section 6.7).
 */
@Service
@Transactional(readOnly = true)
public class TaxSummaryService {

    private static final int MONEY_SCALE = 4;

    private final SecurityTransactionRepository securityTransactionRepository;
    private final DividendRepository dividendRepository;
    private final SmithManeuverService smithManeuverService;
    private final PortfolioRepository portfolioRepository;

    public TaxSummaryService(
            SecurityTransactionRepository securityTransactionRepository,
            DividendRepository dividendRepository,
            SmithManeuverService smithManeuverService,
            PortfolioRepository portfolioRepository
    ) {
        this.securityTransactionRepository = securityTransactionRepository;
        this.dividendRepository = dividendRepository;
        this.smithManeuverService = smithManeuverService;
        this.portfolioRepository = portfolioRepository;
    }

    public TaxSummaryResponse summary(Long portfolioId, Integer year) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }

        var transactionsBySecurity = groupBySecurity(
                securityTransactionRepository.findAllForHoldingsByPortfolio(portfolioId));
        var interestLog = smithManeuverService.getSmithManeuver(portfolioId).interestLog();

        var availableYears = collectAvailableYears(portfolioId, transactionsBySecurity, interestLog);
        int resolvedYear = resolveYear(year, availableYears);

        return new TaxSummaryResponse(
                resolvedYear,
                availableYears,
                buildRealizedGains(transactionsBySecurity, resolvedYear),
                buildDividendIncome(portfolioId, resolvedYear),
                buildInterestSummary(interestLog, resolvedYear)
        );
    }

    private RealizedGains buildRealizedGains(
            Map<Long, List<SecurityTransaction>> transactionsBySecurity,
            int year
    ) {
        var rows = new ArrayList<RealizedGainRow>();
        for (var securityTransactions : transactionsBySecurity.values()) {
            var security = securityTransactions.getFirst().getSecurity();
            int dispositions = 0;
            BigDecimal proceeds = zero();
            BigDecimal gainLoss = zero();
            for (var row : AcbEngine.compute(toInputs(securityTransactions))) {
                if (row.action() != Action.SELL || row.date().getYear() != year) {
                    continue;
                }
                dispositions++;
                proceeds = proceeds.add(nullToZero(row.proceeds()));
                gainLoss = gainLoss.add(nullToZero(row.capitalGainLoss()));
            }
            if (dispositions == 0) {
                continue;
            }
            rows.add(new RealizedGainRow(
                    security.getId(),
                    security.getTicker(),
                    security.getName(),
                    dispositions,
                    scale(proceeds),
                    scale(proceeds.subtract(gainLoss)),
                    scale(gainLoss)
            ));
        }
        rows.sort((a, b) -> a.ticker().compareToIgnoreCase(b.ticker()));

        int totalDispositions = rows.stream().mapToInt(RealizedGainRow::dispositions).sum();
        var total = new RealizedGainRow(
                null,
                null,
                null,
                totalDispositions,
                sumScaled(rows, RealizedGainRow::proceeds),
                sumScaled(rows, RealizedGainRow::acbDisposed),
                sumScaled(rows, RealizedGainRow::gainLoss)
        );
        return new RealizedGains(List.copyOf(rows), total);
    }

    private DividendIncome buildDividendIncome(Long portfolioId, int year) {
        var bySecurity = new LinkedHashMap<Long, DividendAccumulator>();
        for (var dividend : dividendRepository.findByPortfolioIdAndYear(portfolioId, year)) {
            var security = dividend.getSecurity();
            bySecurity.computeIfAbsent(security.getId(), id -> new DividendAccumulator(security)).add(dividend);
        }

        var rows = bySecurity.values().stream()
                .map(DividendAccumulator::toRow)
                .sorted((a, b) -> a.ticker().compareToIgnoreCase(b.ticker()))
                .collect(Collectors.toList());

        var total = new DividendRow(
                null,
                null,
                null,
                sumScaled(rows, DividendRow::gross),
                sumScaled(rows, DividendRow::withholding),
                sumScaled(rows, DividendRow::net)
        );
        return new DividendIncome(List.copyOf(rows), total);
    }

    private InterestSummary buildInterestSummary(List<InterestEntryResponse> interestLog, int year) {
        var chargedByMonth = new LinkedHashMap<Month, BigDecimal>();
        var deductibleByMonth = new LinkedHashMap<Month, BigDecimal>();
        for (var entry : interestLog) {
            if (entry.date().getYear() != year) {
                continue;
            }
            var month = entry.date().getMonth();
            chargedByMonth.merge(month, nullToZero(entry.amount()), BigDecimal::add);
            deductibleByMonth.merge(month, nullToZero(entry.deductibleEstimate()), BigDecimal::add);
        }

        var months = new ArrayList<InterestMonthRow>();
        BigDecimal ytdCharged = zero();
        BigDecimal ytdDeductible = zero();
        for (var month : Month.values()) {
            if (!chargedByMonth.containsKey(month)) {
                continue;
            }
            BigDecimal charged = chargedByMonth.get(month);
            BigDecimal deductible = deductibleByMonth.getOrDefault(month, zero());
            ytdCharged = ytdCharged.add(charged);
            ytdDeductible = ytdDeductible.add(deductible);
            months.add(new InterestMonthRow(monthLabel(month), scale(charged), scale(deductible)));
        }

        var ytd = new InterestMonthRow("YTD", scale(ytdCharged), scale(ytdDeductible));
        return new InterestSummary(List.copyOf(months), ytd);
    }

    private List<Integer> collectAvailableYears(
            Long portfolioId,
            Map<Long, List<SecurityTransaction>> transactionsBySecurity,
            List<InterestEntryResponse> interestLog
    ) {
        var years = new TreeSet<Integer>();
        for (var securityTransactions : transactionsBySecurity.values()) {
            for (var row : AcbEngine.compute(toInputs(securityTransactions))) {
                if (row.action() == Action.SELL) {
                    years.add(row.date().getYear());
                }
            }
        }
        years.addAll(dividendRepository.findDistinctYears(portfolioId));
        for (var entry : interestLog) {
            years.add(entry.date().getYear());
        }
        var descending = new ArrayList<>(years);
        descending.sort((a, b) -> Integer.compare(b, a));
        return List.copyOf(descending);
    }

    private static int resolveYear(Integer requestedYear, List<Integer> availableYears) {
        if (requestedYear != null) {
            return requestedYear;
        }
        if (!availableYears.isEmpty()) {
            return availableYears.getFirst();
        }
        return Year.now().getValue();
    }

    private static Map<Long, List<SecurityTransaction>> groupBySecurity(List<SecurityTransaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(
                        transaction -> transaction.getSecurity().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private static List<SecurityTransactionInput> toInputs(List<SecurityTransaction> transactions) {
        return transactions.stream()
                .map(SecurityTransactionInputs::from)
                .toList();
    }

    private static <T> BigDecimal sumScaled(List<T> rows, java.util.function.Function<T, BigDecimal> field) {
        BigDecimal total = zero();
        for (var row : rows) {
            total = total.add(nullToZero(field.apply(row)));
        }
        return scale(total);
    }

    private static String monthLabel(Month month) {
        return month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? zero() : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO;
    }

    private static final class DividendAccumulator {
        private final Security security;
        private BigDecimal gross = zero();
        private BigDecimal withholding = zero();

        private DividendAccumulator(Security security) {
            this.security = security;
        }

        private void add(Dividend dividend) {
            gross = gross.add(nullToZero(dividend.getGrossAmount()));
            withholding = withholding.add(nullToZero(dividend.getWithholdingTax()));
        }

        private DividendRow toRow() {
            return new DividendRow(
                    security.getId(),
                    security.getTicker(),
                    security.getName(),
                    scale(gross),
                    scale(withholding),
                    scale(gross.subtract(withholding))
            );
        }
    }
}
