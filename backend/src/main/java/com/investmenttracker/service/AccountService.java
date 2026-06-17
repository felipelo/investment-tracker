package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.web.dto.CreateAccountRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    public AccountService(AccountRepository accountRepository, PortfolioRepository portfolioRepository) {
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public Account create(CreateAccountRequest request) {
        if (!portfolioRepository.existsById(request.portfolioId())) {
            throw new ValidationException(Map.of("portfolioId", "Portfolio not found"));
        }

        var currency = request.currency() == null || request.currency().isBlank()
                ? "CAD"
                : request.currency().trim().toUpperCase();

        var account = new Account();
        account.setPortfolio(portfolioRepository.getReferenceById(request.portfolioId()));
        account.setLabel(request.label().trim());
        account.setType(request.type().trim());
        account.setCurrency(currency);

        return accountRepository.save(account);
    }
}
