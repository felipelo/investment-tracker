package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.DividendRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.CashTransactionResponse;
import com.investmenttracker.web.dto.CreateCashTransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CashTransactionService {

    private final CashTransactionRepository cashTransactionRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final SecurityTransactionRepository securityTransactionRepository;
    private final DividendRepository dividendRepository;

    public CashTransactionService(
            CashTransactionRepository cashTransactionRepository,
            AccountRepository accountRepository,
            PortfolioRepository portfolioRepository,
            SecurityTransactionRepository securityTransactionRepository,
            DividendRepository dividendRepository
    ) {
        this.cashTransactionRepository = cashTransactionRepository;
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
        this.securityTransactionRepository = securityTransactionRepository;
        this.dividendRepository = dividendRepository;
    }

    @Transactional(readOnly = true)
    public List<CashTransaction> list(Long portfolioId) {
        requirePortfolio(portfolioId);
        return cashTransactionRepository.findByPortfolioId(portfolioId);
    }

    /**
     * Cash transactions (most recent first) with a per-account running balance stamped on each row.
     * Each transfer leg is its own row, so each leg reflects its own account's balance after that leg.
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResponse> listWithBalances(Long portfolioId) {
        requirePortfolio(portfolioId);

        var entries = new ArrayList<LedgerEntry>();
        for (var tx : cashTransactionRepository.findByPortfolioId(portfolioId)) {
            entries.add(LedgerEntry.cash(tx));
        }
        for (var trade : securityTransactionRepository.findCashImpactingByPortfolio(portfolioId)) {
            entries.add(LedgerEntry.trade(trade));
        }
        for (var dividend : dividendRepository.findCashImpactingByPortfolio(portfolioId)) {
            entries.add(LedgerEntry.dividend(dividend));
        }

        var chronological = Comparator
                .comparing(LedgerEntry::date)
                .thenComparing(LedgerEntry::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(LedgerEntry::sortId);

        var byAccount = new HashMap<Long, List<LedgerEntry>>();
        for (var entry : entries) {
            byAccount.computeIfAbsent(entry.accountId(), k -> new ArrayList<>()).add(entry);
        }
        for (var accountEntries : byAccount.values()) {
            accountEntries.sort(chronological);
            var account = accountEntries.getFirst().account();
            BigDecimal cumulativeNet = BigDecimal.ZERO;
            for (var entry : accountEntries) {
                cumulativeNet = cumulativeNet.add(entry.amount());
                entry.balanceAfter = AccountService
                        .deriveBalance(account.getType(), account.getOpeningBalance(), cumulativeNet)
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        return entries.stream()
                .sorted(chronological.reversed())
                .map(LedgerEntry::toResponse)
                .toList();
    }

    /**
     * A cash leg, a cash-impacting trade, or a cash-impacting dividend, unified for
     * running-balance computation and rendering. Exactly one underlying entity is set.
     */
    private static final class LedgerEntry {
        private final CashTransaction cash;
        private final SecurityTransaction trade;
        private final Dividend dividend;
        private BigDecimal balanceAfter;

        private LedgerEntry(CashTransaction cash, SecurityTransaction trade, Dividend dividend) {
            this.cash = cash;
            this.trade = trade;
            this.dividend = dividend;
        }

        static LedgerEntry cash(CashTransaction cash) {
            return new LedgerEntry(cash, null, null);
        }

        static LedgerEntry trade(SecurityTransaction trade) {
            return new LedgerEntry(null, trade, null);
        }

        static LedgerEntry dividend(Dividend dividend) {
            return new LedgerEntry(null, null, dividend);
        }

        Account account() {
            if (cash != null) return cash.getAccount();
            if (trade != null) return trade.getAccount();
            return dividend.getAccount();
        }

        Long accountId() {
            return account().getId();
        }

        LocalDate date() {
            if (cash != null) return cash.getDate();
            if (trade != null) return trade.getDate();
            return dividend.getPaymentDate();
        }

        Instant createdAt() {
            if (cash != null) return cash.getCreatedAt();
            if (trade != null) return trade.getCreatedAt();
            return dividend.getCreatedAt();
        }

        long sortId() {
            if (cash != null) return cash.getId();
            if (trade != null) return trade.getId();
            return dividend.getId();
        }

        BigDecimal amount() {
            if (cash != null) return cash.getAmount();
            if (trade != null) return trade.cashImpact();
            return dividend.cashImpact();
        }

        CashTransactionResponse toResponse() {
            if (cash != null) return CashTransactionResponse.from(cash, balanceAfter);
            if (trade != null) return CashTransactionResponse.fromTrade(trade, balanceAfter);
            return CashTransactionResponse.fromDividend(dividend, balanceAfter);
        }
    }

    public CashTransaction create(CreateCashTransactionRequest request) {
        validate(request);
        return persistLegs(request);
    }

    public CashTransaction update(Long id, CreateCashTransactionRequest request) {
        validate(request);
        var existing = cashTransactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CashTransaction", id));
        deleteGroup(existing);
        return persistLegs(request);
    }

    public void delete(Long id) {
        var existing = cashTransactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CashTransaction", id));
        deleteGroup(existing);
    }

    private CashTransaction persistLegs(CreateCashTransactionRequest request) {
        var account = requireAccount(request.accountId());
        var magnitude = request.amount();

        if (request.type().requiresCounterparty()) {
            var counterparty = requireAccount(request.counterpartyAccountId());
            var groupId = UUID.randomUUID().toString();
            var sourceLeg = buildLeg(request, account, counterparty, magnitude.negate(), groupId);
            var destLeg = buildLeg(request, counterparty, account, magnitude, groupId);
            var savedSource = cashTransactionRepository.save(sourceLeg);
            cashTransactionRepository.save(destLeg);
            return savedSource;
        }

        var leg = buildLeg(request, account, null, signedAmount(request.type(), magnitude), null);
        return cashTransactionRepository.save(leg);
    }

    private CashTransaction buildLeg(
            CreateCashTransactionRequest request,
            Account account,
            Account counterparty,
            BigDecimal amount,
            String transferGroupId
    ) {
        var leg = new CashTransaction();
        leg.setAccount(account);
        leg.setCounterpartyAccount(counterparty);
        leg.setType(request.type());
        leg.setDate(request.date());
        leg.setAmount(amount);
        leg.setPurpose(request.purpose());
        leg.setTransferGroupId(transferGroupId);
        leg.setNotes(trimToNull(request.notes()));
        return leg;
    }

    private void deleteGroup(CashTransaction transaction) {
        if (transaction.getTransferGroupId() != null) {
            cashTransactionRepository.deleteAll(
                    cashTransactionRepository.findByTransferGroupId(transaction.getTransferGroupId()));
        } else {
            cashTransactionRepository.delete(transaction);
        }
    }

    private static BigDecimal signedAmount(CashTransactionType type, BigDecimal magnitude) {
        return switch (type) {
            case DEPOSIT -> magnitude;
            case WITHDRAWAL, FEE, INTEREST_CHARGE, INTEREST_PAYMENT -> magnitude.negate();
            default -> magnitude;
        };
    }

    private void validate(CreateCashTransactionRequest request) {
        var errors = new LinkedHashMap<String, String>();
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("amount", "Amount must be greater than zero");
        }
        if (!accountRepository.existsById(request.accountId())) {
            errors.put("accountId", "Account not found");
        }
        if (request.type() != null && request.type().requiresCounterparty()) {
            if (request.counterpartyAccountId() == null) {
                errors.put("counterpartyAccountId", "Counterparty account is required for this type");
            } else if (request.counterpartyAccountId().equals(request.accountId())) {
                errors.put("counterpartyAccountId", "Counterparty must differ from the account");
            } else if (!accountRepository.existsById(request.counterpartyAccountId())) {
                errors.put("counterpartyAccountId", "Counterparty account not found");
            }
        } else if (request.counterpartyAccountId() != null) {
            errors.put("counterpartyAccountId", "Counterparty is only allowed for transfer types");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private Account requireAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account", id));
    }

    private void requirePortfolio(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
