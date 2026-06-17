package com.investmenttracker.service;

import com.investmenttracker.domain.Portfolio;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.web.dto.CreatePortfolioRequest;
import com.investmenttracker.web.dto.PortfolioResponse;
import com.investmenttracker.web.dto.UpdatePortfolioRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final HoldingService holdingService;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            HoldingService holdingService
    ) {
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.holdingService = holdingService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> list() {
        return portfolioRepository.findAllByOrderByNameAsc().stream()
                .map(portfolio -> PortfolioResponse.from(portfolio, holdingService.portfolioMetrics(portfolio.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioResponse get(Long id) {
        var portfolio = requirePortfolio(id);
        return PortfolioResponse.from(portfolio, holdingService.portfolioMetrics(id));
    }

    public PortfolioResponse create(CreatePortfolioRequest request) {
        var name = request.name().trim();
        if (portfolioRepository.existsByName(name)) {
            throw new ValidationException(Map.of("name", "A portfolio with this name already exists"));
        }

        var portfolio = new Portfolio();
        portfolio.setName(name);
        portfolio.setDescription(trimToNull(request.description()));
        portfolio.setBaseCurrency(normalizeCurrency(request.baseCurrency()));
        portfolio.setType(trimToNull(request.type()));

        var saved = portfolioRepository.save(portfolio);
        return PortfolioResponse.from(saved, holdingService.portfolioMetrics(saved.getId()));
    }

    public PortfolioResponse update(Long id, UpdatePortfolioRequest request) {
        var portfolio = requirePortfolio(id);
        var name = request.name().trim();
        if (portfolioRepository.existsByNameAndIdNot(name, id)) {
            throw new ValidationException(Map.of("name", "A portfolio with this name already exists"));
        }

        portfolio.setName(name);
        portfolio.setDescription(trimToNull(request.description()));
        portfolio.setBaseCurrency(normalizeCurrency(request.baseCurrency()));
        portfolio.setType(trimToNull(request.type()));

        var saved = portfolioRepository.save(portfolio);
        return PortfolioResponse.from(saved, holdingService.portfolioMetrics(saved.getId()));
    }

    public void delete(Long id) {
        requirePortfolio(id);
        if (accountRepository.existsByPortfolioId(id)) {
            throw new ValidationException(Map.of(
                    "portfolio", "Cannot delete a portfolio that still has accounts"
            ));
        }
        portfolioRepository.deleteById(id);
    }

    private Portfolio requirePortfolio(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Portfolio", id));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "CAD" : currency.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
