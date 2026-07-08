package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.web.dto.CreateDividendRequest;
import com.investmenttracker.web.dto.DividendSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional
public class DividendService {

    private static final int MONEY_SCALE = 4;

    private final DividendRepository dividendRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityRepository securityRepository;
    private final AccountRepository accountRepository;

    public DividendService(
            DividendRepository dividendRepository,
            PortfolioRepository portfolioRepository,
            SecurityRepository securityRepository,
            AccountRepository accountRepository
    ) {
        this.dividendRepository = dividendRepository;
        this.portfolioRepository = portfolioRepository;
        this.securityRepository = securityRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Dividend> list(Long portfolioId) {
        requirePortfolio(portfolioId);
        return dividendRepository.findByPortfolioIdOrderByPaymentDateDesc(portfolioId);
    }

    public Dividend create(CreateDividendRequest request) {
        validate(request);
        var portfolio = requirePortfolioEntity(request.portfolioId());
        var dividend = new Dividend();
        dividend.setPortfolio(portfolio);
        applyRequest(dividend, request, portfolio);
        return dividendRepository.save(dividend);
    }

    public Dividend update(Long id, CreateDividendRequest request) {
        validate(request);
        var dividend = dividendRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dividend", id));
        var portfolio = requirePortfolioEntity(request.portfolioId());
        dividend.setPortfolio(portfolio);
        applyRequest(dividend, request, portfolio);
        return dividendRepository.save(dividend);
    }

    public void delete(Long id) {
        if (!dividendRepository.existsById(id)) {
            throw new NotFoundException("Dividend", id);
        }
        dividendRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DividendSummaryResponse summary(Long portfolioId, Integer year) {
        requirePortfolio(portfolioId);
        var availableYears = dividendRepository.findDistinctYears(portfolioId);
        int resolvedYear = resolveYear(year, availableYears);

        var months = new ArrayList<BigDecimal>(12);
        for (int i = 0; i < 12; i++) {
            months.add(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        for (var dividend : dividendRepository.findByPortfolioIdAndYear(portfolioId, resolvedYear)) {
            int monthIndex = dividend.getPaymentDate().getMonthValue() - 1;
            months.set(monthIndex, months.get(monthIndex).add(dividend.getNetAmount()));
        }

        var cumulative = new ArrayList<BigDecimal>(12);
        BigDecimal running = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (var monthTotal : months) {
            running = running.add(monthTotal);
            cumulative.add(running);
        }

        return new DividendSummaryResponse(resolvedYear, months, cumulative, running, availableYears);
    }

    private int resolveYear(Integer requestedYear, List<Integer> availableYears) {
        if (requestedYear != null) {
            return requestedYear;
        }
        if (!availableYears.isEmpty()) {
            return availableYears.getFirst();
        }
        return Year.now().getValue();
    }

    private void validate(CreateDividendRequest request) {
        var errors = new LinkedHashMap<String, String>();
        if (!portfolioRepository.existsById(request.portfolioId())) {
            errors.put("portfolioId", "Portfolio not found");
        }
        if (!securityRepository.existsById(request.securityId())) {
            errors.put("securityId", "Security not found");
        }
        if (request.accountId() != null && !accountRepository.existsById(request.accountId())) {
            errors.put("accountId", "Account not found");
        }
        BigDecimal withholding = request.withholdingTax() != null ? request.withholdingTax() : BigDecimal.ZERO;
        if (request.grossAmount() != null && withholding.compareTo(request.grossAmount()) > 0) {
            errors.put("withholdingTax", "Withholding tax cannot exceed the gross amount");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void applyRequest(Dividend dividend, CreateDividendRequest request, Portfolio portfolio) {
        var security = securityRepository.findById(request.securityId())
                .orElseThrow(() -> new NotFoundException("Security", request.securityId()));
        dividend.setSecurity(security);
        dividend.setAccount(resolveAccount(request.accountId()));
        dividend.setPaymentDate(request.paymentDate());
        dividend.setGrossAmount(request.grossAmount());
        dividend.setWithholdingTax(request.withholdingTax() != null ? request.withholdingTax() : BigDecimal.ZERO);
        dividend.setCurrency(resolveCurrency(request.currency(), portfolio));
        dividend.setDrip(Boolean.TRUE.equals(request.drip()));
        dividend.setNotes(trimToNull(request.notes()));
    }

    private Account resolveAccount(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account", accountId));
    }

    private static String resolveCurrency(String currency, Portfolio portfolio) {
        if (currency != null && !currency.isBlank()) {
            return currency.trim().toUpperCase();
        }
        return portfolio.getBaseCurrency();
    }

    private Portfolio requirePortfolioEntity(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio", portfolioId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requirePortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }
    }
}
