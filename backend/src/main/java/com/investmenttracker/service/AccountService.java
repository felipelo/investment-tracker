package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.CreateAccountRequest;
import com.investmenttracker.web.dto.UpdateAccountRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AccountService {

    private static final java.util.Set<String> CREDIT_LINE_TYPES = java.util.Set.of("HELOC", "Margin");

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityTransactionRepository securityTransactionRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final DividendRepository dividendRepository;

    public AccountService(
            AccountRepository accountRepository,
            PortfolioRepository portfolioRepository,
            SecurityTransactionRepository securityTransactionRepository,
            CashTransactionRepository cashTransactionRepository,
            DividendRepository dividendRepository
    ) {
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.securityTransactionRepository = securityTransactionRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.dividendRepository = dividendRepository;
    }

    public Account create(CreateAccountRequest request) {
        if (!portfolioRepository.existsById(request.portfolioId())) {
            throw new ValidationException(Map.of("portfolioId", "Portfolio not found"));
        }

        var account = new Account();
        account.setPortfolio(portfolioRepository.getReferenceById(request.portfolioId()));
        applyFields(account, request.label(), request.type(), request.institution(), request.currency(),
                request.openingBalance(), request.openingBalanceDate(),
                request.creditLimit(), request.interestRate());

        return accountRepository.save(account);
    }

    public Account update(Long id, UpdateAccountRequest request) {
        var account = requireAccount(id);
        applyFields(account, request.label(), request.type(), request.institution(), request.currency(),
                request.openingBalance(), request.openingBalanceDate(),
                request.creditLimit(), request.interestRate());

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> currentBalances(List<Account> accounts) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        var ids = accounts.stream().map(Account::getId).toList();
        var netByAccount = new java.util.HashMap<Long, BigDecimal>();
        for (var row : cashTransactionRepository.sumAmountByAccountIds(ids)) {
            netByAccount.put((Long) row[0], (BigDecimal) row[1]);
        }
        for (var trade : securityTransactionRepository.findCashImpactingByAccountIds(ids)) {
            netByAccount.merge(trade.getAccount().getId(), trade.cashImpact(), BigDecimal::add);
        }
        for (var dividend : dividendRepository.findCashImpactingByAccountIds(ids)) {
            netByAccount.merge(dividend.getAccount().getId(), dividend.cashImpact(), BigDecimal::add);
        }
        var balances = new LinkedHashMap<Long, BigDecimal>();
        for (var account : accounts) {
            BigDecimal net = netByAccount.getOrDefault(account.getId(), BigDecimal.ZERO);
            balances.put(account.getId(), deriveBalance(account.getType(), account.getOpeningBalance(), net));
        }
        return balances;
    }

    @Transactional(readOnly = true)
    public BigDecimal currentBalance(Account account) {
        return currentBalances(List.of(account)).get(account.getId());
    }

    /**
     * Current balance from opening balance plus signed cash legs. Credit-line accounts
     * (HELOC/Margin) report a positive amount owed, matching SmithManeuverService.
     */
    static BigDecimal deriveBalance(String type, BigDecimal openingBalance, BigDecimal netMovement) {
        BigDecimal opening = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        BigDecimal net = netMovement == null ? BigDecimal.ZERO : netMovement;
        return CREDIT_LINE_TYPES.contains(type) ? opening.subtract(net) : opening.add(net);
    }

    public void delete(Long id) {
        requireAccount(id);
        if (securityTransactionRepository.existsByAccountId(id)) {
            throw new ValidationException(Map.of(
                    "account", "Cannot delete an account that has security transactions"
            ));
        }
        if (cashTransactionRepository.existsByAccountId(id)
                || cashTransactionRepository.existsByCounterpartyAccountId(id)) {
            throw new ValidationException(Map.of(
                    "account", "Cannot delete an account that has cash transactions"
            ));
        }
        accountRepository.deleteById(id);
    }

    private void applyFields(
            Account account,
            String label,
            String type,
            String institution,
            String currency,
            BigDecimal openingBalance,
            java.time.LocalDate openingBalanceDate,
            BigDecimal creditLimit,
            BigDecimal interestRate
    ) {
        account.setLabel(label.trim());
        account.setType(type.trim());
        account.setInstitution(trimToNull(institution));
        account.setCurrency(normalizeCurrency(currency));
        account.setOpeningBalance(openingBalance == null ? BigDecimal.ZERO : openingBalance);
        account.setOpeningBalanceDate(openingBalanceDate);
        applyCreditLineFields(account, account.getType(), creditLimit, interestRate);
    }

    private static void applyCreditLineFields(
            Account account,
            String type,
            BigDecimal creditLimit,
            BigDecimal interestRate
    ) {
        if (CREDIT_LINE_TYPES.contains(type)) {
            account.setCreditLimit(creditLimit);
            account.setInterestRate(interestRate);
        } else {
            account.setCreditLimit(null);
            account.setInterestRate(null);
        }
    }

    private Account requireAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account", id));
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
