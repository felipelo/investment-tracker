package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.web.dto.CreateAccountRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account create(CreateAccountRequest request) {
        var currency = request.currency() == null || request.currency().isBlank()
                ? "CAD"
                : request.currency().trim().toUpperCase();

        var account = new Account();
        account.setLabel(request.label().trim());
        account.setType(request.type().trim());
        account.setCurrency(currency);

        return accountRepository.save(account);
    }
}
