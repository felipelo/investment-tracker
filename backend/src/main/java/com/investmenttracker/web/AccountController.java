package com.investmenttracker.web;

import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.service.AccountService;
import com.investmenttracker.web.dto.AccountResponse;
import com.investmenttracker.web.dto.CreateAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public AccountController(AccountRepository accountRepository, AccountService accountService) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "List accounts (dropdown lookup), optionally scoped to a portfolio")
    public List<AccountResponse> listAccounts(@RequestParam(required = false) Long portfolioId) {
        var accounts = portfolioId != null
                ? accountRepository.findByPortfolioIdOrderByLabelAsc(portfolioId)
                : accountRepository.findAllByOrderByLabelAsc();
        return accounts.stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accountService.create(request));
    }
}
