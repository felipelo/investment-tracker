package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.domain.SmithManeuverFlow;
import com.investmenttracker.domain.SmithManeuverFlowStep;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityTransactionRepository;
import com.investmenttracker.repository.SmithManeuverFlowRepository;
import com.investmenttracker.web.dto.CreateSmithManeuverFlowRequest;
import com.investmenttracker.web.dto.SmithManeuverFlowResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional
public class SmithManeuverFlowService {

    private static final String HELOC_TYPE = "HELOC";

    private final SmithManeuverFlowRepository flowRepository;
    private final PortfolioRepository portfolioRepository;
    private final AccountRepository accountRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final SecurityTransactionRepository securityTransactionRepository;

    public SmithManeuverFlowService(
            SmithManeuverFlowRepository flowRepository,
            PortfolioRepository portfolioRepository,
            AccountRepository accountRepository,
            CashTransactionRepository cashTransactionRepository,
            SecurityTransactionRepository securityTransactionRepository
    ) {
        this.flowRepository = flowRepository;
        this.portfolioRepository = portfolioRepository;
        this.accountRepository = accountRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.securityTransactionRepository = securityTransactionRepository;
    }

    @Transactional(readOnly = true)
    public List<SmithManeuverFlow> list(Long portfolioId) {
        requirePortfolio(portfolioId);
        return flowRepository.findByPortfolioIdWithSteps(portfolioId);
    }

    public SmithManeuverFlowResponse create(CreateSmithManeuverFlowRequest request) {
        validate(request);
        var flow = new SmithManeuverFlow();
        flow.setPortfolio(portfolioRepository.getReferenceById(request.portfolioId()));
        applyRequest(flow, request);
        return FlowMapper.toResponse(flowRepository.save(flow));
    }

    public SmithManeuverFlowResponse update(Long id, CreateSmithManeuverFlowRequest request) {
        validate(request);
        var flow = flowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SmithManeuverFlow", id));
        flow.setPortfolio(portfolioRepository.getReferenceById(request.portfolioId()));
        flow.clearSteps();
        applyRequest(flow, request);
        return FlowMapper.toResponse(flowRepository.save(flow));
    }

    public void delete(Long id) {
        if (!flowRepository.existsById(id)) {
            throw new NotFoundException("SmithManeuverFlow", id);
        }
        flowRepository.deleteById(id);
    }

    private void applyRequest(SmithManeuverFlow flow, CreateSmithManeuverFlowRequest request) {
        flow.setHelocAccount(accountRepository.getReferenceById(request.helocAccountId()));
        flow.setInvestmentUseAmount(request.investmentUseAmount());
        flow.setLabel(resolveLabel(request.label()));
        flow.setNotes(trimToNull(request.notes()));

        int order = 0;
        for (Long cashId : safeList(request.cashTransactionIds())) {
            var step = new SmithManeuverFlowStep();
            step.setStepOrder(order++);
            step.setCashTransaction(cashTransactionRepository.getReferenceById(cashId));
            flow.addStep(step);
        }
        if (request.securityTransactionId() != null) {
            var step = new SmithManeuverFlowStep();
            step.setStepOrder(order);
            step.setSecurityTransaction(securityTransactionRepository.getReferenceById(request.securityTransactionId()));
            flow.addStep(step);
        }
    }

    private void validate(CreateSmithManeuverFlowRequest request) {
        var errors = new LinkedHashMap<String, String>();

        boolean portfolioExists = portfolioRepository.existsById(request.portfolioId());
        if (!portfolioExists) {
            errors.put("portfolioId", "Portfolio not found");
        }

        Account heloc = accountRepository.findById(request.helocAccountId()).orElse(null);
        if (heloc == null) {
            errors.put("helocAccountId", "Account not found");
        } else {
            if (portfolioExists && !heloc.getPortfolio().getId().equals(request.portfolioId())) {
                errors.put("helocAccountId", "Account does not belong to this portfolio");
            }
            if (!HELOC_TYPE.equalsIgnoreCase(heloc.getType())) {
                errors.put("helocAccountId", "Source account must be a HELOC account");
            }
        }

        var cashIds = safeList(request.cashTransactionIds());
        if (cashIds.isEmpty()) {
            errors.put("cashTransactionIds", "At least one cash transaction (the HELOC draw) is required");
        } else {
            for (Long cashId : cashIds) {
                CashTransaction cash = cashTransactionRepository.findById(cashId).orElse(null);
                if (cash == null) {
                    errors.put("cashTransactionIds", "Cash transaction not found: " + cashId);
                    break;
                }
                if (portfolioExists && !cash.getAccount().getPortfolio().getId().equals(request.portfolioId())) {
                    errors.put("cashTransactionIds", "Cash transaction does not belong to this portfolio: " + cashId);
                    break;
                }
            }
        }

        if (request.securityTransactionId() != null) {
            SecurityTransaction security =
                    securityTransactionRepository.findById(request.securityTransactionId()).orElse(null);
            if (security == null) {
                errors.put("securityTransactionId", "Security transaction not found");
            } else if (portfolioExists
                    && !security.getAccount().getPortfolio().getId().equals(request.portfolioId())) {
                errors.put("securityTransactionId", "Security transaction does not belong to this portfolio");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static String resolveLabel(String label) {
        var trimmed = trimToNull(label);
        return trimmed != null ? trimmed : "Smith Maneuver flow";
    }

    private static List<Long> safeList(List<Long> ids) {
        return ids != null ? ids : List.of();
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
