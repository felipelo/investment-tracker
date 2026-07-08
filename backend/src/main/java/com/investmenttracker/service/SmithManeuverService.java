package com.investmenttracker.service;

import com.investmenttracker.domain.Account;
import com.investmenttracker.domain.CashPurpose;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.CashTransactionType;
import com.investmenttracker.domain.FlowStatus;
import com.investmenttracker.domain.SmithManeuverFlow;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.CashTransactionRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SmithManeuverFlowRepository;
import com.investmenttracker.web.dto.HelocAccountSummaryResponse;
import com.investmenttracker.web.dto.InterestEntryResponse;
import com.investmenttracker.web.dto.SmithManeuverResponse;
import com.investmenttracker.web.dto.SmithManeuverWarningResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only Smith Maneuver aggregation: derives HELOC owed balances, the investment-use balance,
 * traced percentages and the per-interest deductible estimate (REQUIREMENTS.md section 6.4).
 * This is a record-keeping estimate, not tax advice.
 */
@Service
@Transactional(readOnly = true)
public class SmithManeuverService {

    private static final String HELOC_TYPE = "HELOC";
    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;
    private static final int FRACTION_SCALE = 6;

    private final SmithManeuverFlowRepository flowRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    public SmithManeuverService(
            SmithManeuverFlowRepository flowRepository,
            CashTransactionRepository cashTransactionRepository,
            AccountRepository accountRepository,
            PortfolioRepository portfolioRepository
    ) {
        this.flowRepository = flowRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.accountRepository = accountRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public SmithManeuverResponse getSmithManeuver(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NotFoundException("Portfolio", portfolioId);
        }

        var helocAccounts = accountRepository.findByPortfolioIdOrderByLabelAsc(portfolioId).stream()
                .filter(a -> HELOC_TYPE.equalsIgnoreCase(a.getType()))
                .toList();
        var cashTransactions = cashTransactionRepository.findByPortfolioId(portfolioId);
        var flows = flowRepository.findByPortfolioIdWithSteps(portfolioId);

        var helocSummaries = new ArrayList<HelocAccountSummaryResponse>();
        BigDecimal totalInvestmentUse = zero();
        for (var account : helocAccounts) {
            BigDecimal owed = owedBalanceAsOf(account.getId(), cashTransactions, null);
            BigDecimal investmentUse = investmentUseAsOf(account.getId(), flows, null).min(owed.max(zero()));
            BigDecimal tracedPct = ratio(investmentUse, owed)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PCT_SCALE, RoundingMode.HALF_UP);
            totalInvestmentUse = totalInvestmentUse.add(investmentUse);
            helocSummaries.add(new HelocAccountSummaryResponse(
                    account.getId(),
                    account.getLabel(),
                    owed.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    account.getCreditLimit(),
                    account.getInterestRate(),
                    investmentUse.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    tracedPct,
                    tracedStatusLabel(tracedPct)
            ));
        }

        var flowResponses = flows.stream().map(FlowMapper::toResponse).toList();
        var interestLog = buildInterestLog(helocAccounts, cashTransactions, flows);
        var warnings = buildWarnings(helocAccounts, cashTransactions, flows);

        return new SmithManeuverResponse(
                totalInvestmentUse.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                flowResponses,
                helocSummaries,
                interestLog,
                warnings
        );
    }

    private List<InterestEntryResponse> buildInterestLog(
            List<Account> helocAccounts,
            List<CashTransaction> cashTransactions,
            List<SmithManeuverFlow> flows
    ) {
        var helocIds = helocAccounts.stream().map(Account::getId).toList();
        var entries = new ArrayList<InterestEntryResponse>();
        for (var tx : cashTransactions) {
            if (!isInterest(tx.getType()) || !helocIds.contains(tx.getAccount().getId())) {
                continue;
            }
            BigDecimal owed = owedBalanceAsOf(tx.getAccount().getId(), cashTransactions, tx.getDate());
            BigDecimal investmentUse = investmentUseAsOf(tx.getAccount().getId(), flows, tx.getDate());
            BigDecimal fraction = ratio(investmentUse, owed);
            BigDecimal amount = tx.getAmount().abs();
            BigDecimal deductible = amount.multiply(fraction).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            entries.add(new InterestEntryResponse(
                    tx.getId(),
                    tx.getDate(),
                    interestTypeLabel(tx.getType()),
                    amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    deductible
            ));
        }
        return entries;
    }

    private List<SmithManeuverWarningResponse> buildWarnings(
            List<Account> helocAccounts,
            List<CashTransaction> cashTransactions,
            List<SmithManeuverFlow> flows
    ) {
        var warnings = new ArrayList<SmithManeuverWarningResponse>();
        for (var flow : flows) {
            var status = FlowMapper.deriveStatus(flow);
            if (status == FlowStatus.PARTIALLY_TRACED) {
                warnings.add(new SmithManeuverWarningResponse(
                        "Partially traced flow — " + flow.getLabel(),
                        "Borrowed funds are not fully linked to an investment purchase. Review the chain and purpose tags."
                ));
            } else if (status == FlowStatus.UNTRACED) {
                warnings.add(new SmithManeuverWarningResponse(
                        "Untraced flow — " + flow.getLabel(),
                        "No investment-tagged leg. Does not count toward the deductible base."
                ));
            }
        }
        for (var account : helocAccounts) {
            if (commingled(account.getId(), cashTransactions)) {
                warnings.add(new SmithManeuverWarningResponse(
                        "Mixed-use account — " + account.getLabel(),
                        "This account holds both investment and personal money flows, which weakens the deductibility tracing."
                ));
            }
        }
        return warnings;
    }

    /** Owed balance = opening balance minus the signed legs booked to the account up to {@code asOf} (inclusive). */
    private BigDecimal owedBalanceAsOf(Long accountId, List<CashTransaction> cashTransactions, LocalDate asOf) {
        BigDecimal opening = accountRepository.findById(accountId)
                .map(Account::getOpeningBalance)
                .orElse(zero());
        BigDecimal movements = zero();
        for (var tx : cashTransactions) {
            if (!tx.getAccount().getId().equals(accountId)) {
                continue;
            }
            if (asOf != null && tx.getDate().isAfter(asOf)) {
                continue;
            }
            movements = movements.add(tx.getAmount());
        }
        return opening.subtract(movements);
    }

    /** Sum of investment-use amounts for non-untraced flows on the account whose draw date is on or before {@code asOf}. */
    private BigDecimal investmentUseAsOf(Long accountId, List<SmithManeuverFlow> flows, LocalDate asOf) {
        BigDecimal total = zero();
        for (var flow : flows) {
            if (!flow.getHelocAccount().getId().equals(accountId)) {
                continue;
            }
            if (FlowMapper.deriveStatus(flow) == FlowStatus.UNTRACED) {
                continue;
            }
            if (asOf != null) {
                var drawDate = FlowMapper.drawDate(flow);
                if (drawDate != null && drawDate.isAfter(asOf)) {
                    continue;
                }
            }
            total = total.add(flow.getInvestmentUseAmount());
        }
        return total;
    }

    private boolean commingled(Long accountId, List<CashTransaction> cashTransactions) {
        boolean investment = false;
        boolean personal = false;
        for (var tx : cashTransactions) {
            if (!tx.getAccount().getId().equals(accountId) || tx.getPurpose() == null) {
                continue;
            }
            if (tx.getPurpose() == CashPurpose.INVESTMENT) {
                investment = true;
            } else if (tx.getPurpose() == CashPurpose.PERSONAL) {
                personal = true;
            }
        }
        return investment && personal;
    }

    /** investmentUse / owed, clamped to [0, 1]. */
    private static BigDecimal ratio(BigDecimal investmentUse, BigDecimal owed) {
        if (owed.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fraction = investmentUse.divide(owed, FRACTION_SCALE, RoundingMode.HALF_UP);
        if (fraction.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        if (fraction.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return fraction;
    }

    private static String tracedStatusLabel(BigDecimal tracedPct) {
        double pct = tracedPct.doubleValue();
        if (pct >= 99.5) {
            return "Fully traced";
        }
        if (pct >= 75.0) {
            return "Mostly traced";
        }
        if (pct > 0.0) {
            return "Partially traced";
        }
        return "Untraced";
    }

    private static boolean isInterest(CashTransactionType type) {
        return type == CashTransactionType.INTEREST_CHARGE || type == CashTransactionType.INTEREST_PAYMENT;
    }

    private static String interestTypeLabel(CashTransactionType type) {
        return type == CashTransactionType.INTEREST_CHARGE ? "Interest Charge" : "Interest Payment";
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
