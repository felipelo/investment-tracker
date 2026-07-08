package com.investmenttracker.web;

import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.service.AccountService;
import com.investmenttracker.web.dto.AccountResponse;
import com.investmenttracker.web.dto.CreateAccountRequest;
import com.investmenttracker.web.dto.UpdateAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    @Operation(summary = "List accounts, optionally scoped to a portfolio")
    public List<AccountResponse> listAccounts(@RequestParam(required = false) Long portfolioId) {
        var accounts = portfolioId != null
                ? accountRepository.findByPortfolioIdOrderByLabelAsc(portfolioId)
                : accountRepository.findAllByOrderByLabelAsc();
        var balances = accountService.currentBalances(accounts);
        return accounts.stream()
                .map(account -> AccountResponse.from(account, balances.get(account.getId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account")
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        var account = accountService.create(request);
        return AccountResponse.from(account, accountService.currentBalance(account));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    public AccountResponse updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        var account = accountService.update(id, request);
        return AccountResponse.from(account, accountService.currentBalance(account));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account (only when it has no security transactions)")
    public void deleteAccount(@PathVariable Long id) {
        accountService.delete(id);
    }
}
