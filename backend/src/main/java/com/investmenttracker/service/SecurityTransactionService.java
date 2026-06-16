package com.investmenttracker.service;

import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.web.dto.CreateSecurityTransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SecurityTransactionService {

    private final SecurityTransactionRepository securityTransactionRepository;
    private final SecurityRepository securityRepository;
    private final AccountRepository accountRepository;

    public SecurityTransactionService(
            SecurityTransactionRepository securityTransactionRepository,
            SecurityRepository securityRepository,
            AccountRepository accountRepository
    ) {
        this.securityTransactionRepository = securityTransactionRepository;
        this.securityRepository = securityRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<SecurityTransaction> list(Long securityId, Long accountId, LocalDate from, LocalDate to) {
        return securityTransactionRepository.findFiltered(securityId, accountId, from, to);
    }

    @Transactional(readOnly = true)
    public SecurityTransaction getById(Long id) {
        return securityTransactionRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("SecurityTransaction", id));
    }

    public SecurityTransaction create(CreateSecurityTransactionRequest request) {
        validateReferences(request);
        validateActionFields(request);

        var transaction = new SecurityTransaction();
        applyRequest(transaction, request);

        return securityTransactionRepository.save(transaction);
    }

    public SecurityTransaction update(Long id, CreateSecurityTransactionRequest request) {
        var transaction = getById(id);
        validateReferences(request);
        validateActionFields(request);

        applyRequest(transaction, request);

        return securityTransactionRepository.save(transaction);
    }

    public void delete(Long id) {
        if (!securityTransactionRepository.existsById(id)) {
            throw new NotFoundException("SecurityTransaction", id);
        }
        securityTransactionRepository.deleteById(id);
    }

    private void applyRequest(SecurityTransaction transaction, CreateSecurityTransactionRequest request) {
        transaction.setDate(request.date());
        transaction.setSecurity(securityRepository.getReferenceById(request.securityId()));
        transaction.setAccount(request.accountId() != null
                ? accountRepository.getReferenceById(request.accountId())
                : null);
        transaction.setAction(request.action());
        transaction.setShares(normalizedShares(request));
        transaction.setPricePerShare(normalizedPricePerShare(request));
        transaction.setCommission(normalizedCommission(request));
        transaction.setCashAmount(normalizedCashAmount(request));
        transaction.setSplitRatio(normalizedSplitRatio(request));
        transaction.setNotes(request.notes());
    }

    private void validateReferences(CreateSecurityTransactionRequest request) {
        if (!securityRepository.existsById(request.securityId())) {
            throw new ValidationException(Map.of("securityId", "Security not found"));
        }
        if (request.accountId() != null && !accountRepository.existsById(request.accountId())) {
            throw new ValidationException(Map.of("accountId", "Account not found"));
        }
    }

    private void validateActionFields(CreateSecurityTransactionRequest request) {
        var errors = new LinkedHashMap<String, String>();

        switch (request.action()) {
            case BUY, SELL -> validateBuySell(request, errors);
            case RETURN_OF_CAPITAL, REINVESTED_DISTRIBUTION -> validateDistribution(request, errors);
            case SPLIT -> validateSplit(request, errors);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void validateBuySell(CreateSecurityTransactionRequest request, Map<String, String> errors) {
        if (request.shares() == null || request.shares().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("shares", "Shares must be greater than zero for Buy and Sell");
        }
        if (request.pricePerShare() == null || request.pricePerShare().compareTo(BigDecimal.ZERO) < 0) {
            errors.put("pricePerShare", "Price per share must be zero or greater for Buy and Sell");
        }
        if (request.cashAmount() != null) {
            errors.put("cashAmount", "Cash amount is not allowed for Buy and Sell");
        }
        if (request.splitRatio() != null) {
            errors.put("splitRatio", "Split ratio is not allowed for Buy and Sell");
        }
    }

    private void validateDistribution(CreateSecurityTransactionRequest request, Map<String, String> errors) {
        if (request.cashAmount() == null) {
            errors.put("cashAmount", "Cash amount is required for Return of Capital and Reinvested Distribution");
        }
        if (request.shares() != null) {
            errors.put("shares", "Shares are not allowed for Return of Capital and Reinvested Distribution");
        }
        if (request.pricePerShare() != null) {
            errors.put("pricePerShare", "Price per share is not allowed for Return of Capital and Reinvested Distribution");
        }
        if (request.splitRatio() != null) {
            errors.put("splitRatio", "Split ratio is not allowed for Return of Capital and Reinvested Distribution");
        }
    }

    private void validateSplit(CreateSecurityTransactionRequest request, Map<String, String> errors) {
        if (request.splitRatio() == null || request.splitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("splitRatio", "Split ratio must be greater than zero for Split");
        }
        if (request.shares() != null) {
            errors.put("shares", "Shares are not allowed for Split");
        }
        if (request.pricePerShare() != null) {
            errors.put("pricePerShare", "Price per share is not allowed for Split");
        }
        if (request.cashAmount() != null) {
            errors.put("cashAmount", "Cash amount is not allowed for Split");
        }
    }

    private BigDecimal normalizedShares(CreateSecurityTransactionRequest request) {
        return request.action() == Action.BUY || request.action() == Action.SELL ? request.shares() : null;
    }

    private BigDecimal normalizedPricePerShare(CreateSecurityTransactionRequest request) {
        return request.action() == Action.BUY || request.action() == Action.SELL ? request.pricePerShare() : null;
    }

    private BigDecimal normalizedCommission(CreateSecurityTransactionRequest request) {
        if (request.action() != Action.BUY && request.action() != Action.SELL) {
            return null;
        }
        return request.commission() != null ? request.commission() : BigDecimal.ZERO;
    }

    private BigDecimal normalizedCashAmount(CreateSecurityTransactionRequest request) {
        return request.action() == Action.RETURN_OF_CAPITAL || request.action() == Action.REINVESTED_DISTRIBUTION
                ? request.cashAmount()
                : null;
    }

    private BigDecimal normalizedSplitRatio(CreateSecurityTransactionRequest request) {
        return request.action() == Action.SPLIT ? request.splitRatio() : null;
    }
}
