package com.investmenttracker.service;

import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.CashTransaction;
import com.investmenttracker.domain.FlowStatus;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.domain.SmithManeuverFlow;
import com.investmenttracker.domain.SmithManeuverFlowStep;
import com.investmenttracker.web.dto.FlowStepResponse;
import com.investmenttracker.web.dto.SmithManeuverFlowResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Maps a {@link SmithManeuverFlow} to its API representation and derives its tracing status. */
public final class FlowMapper {

    private FlowMapper() {
    }

    /**
     * A flow is {@code TRACED} when borrowed money is fully linked to an investment purchase:
     * at least one investment-tagged leg, a terminal Buy, and no personal-tagged leg. A draw with
     * investment intent but no completed purchase (or a mixed-purpose chain) is {@code PARTIALLY_TRACED};
     * a chain with no investment-tagged leg at all is {@code UNTRACED}.
     */
    static FlowStatus deriveStatus(SmithManeuverFlow flow) {
        boolean hasBuy = false;
        boolean anyInvestment = false;
        boolean anyPersonal = false;

        for (var step : flow.getSteps()) {
            var security = step.getSecurityTransaction();
            if (security != null && security.getAction() == Action.BUY) {
                hasBuy = true;
            }
            var cash = step.getCashTransaction();
            if (cash != null && cash.getPurpose() != null) {
                switch (cash.getPurpose()) {
                    case INVESTMENT -> anyInvestment = true;
                    case PERSONAL -> anyPersonal = true;
                }
            }
        }

        if (!anyInvestment) {
            return FlowStatus.UNTRACED;
        }
        if (hasBuy && !anyPersonal) {
            return FlowStatus.TRACED;
        }
        return FlowStatus.PARTIALLY_TRACED;
    }

    /** Earliest cash-leg date in the flow (the draw date), or {@code null} when no cash legs exist. */
    static LocalDate drawDate(SmithManeuverFlow flow) {
        LocalDate earliest = null;
        for (var step : flow.getSteps()) {
            var cash = step.getCashTransaction();
            if (cash != null && (earliest == null || cash.getDate().isBefore(earliest))) {
                earliest = cash.getDate();
            }
        }
        return earliest;
    }

    public static SmithManeuverFlowResponse toResponse(SmithManeuverFlow flow) {
        return new SmithManeuverFlowResponse(
                flow.getId(),
                flow.getPortfolio().getId(),
                flow.getHelocAccount().getId(),
                flow.getHelocAccount().getLabel(),
                flow.getLabel(),
                flow.getInvestmentUseAmount(),
                deriveStatus(flow).name(),
                flow.getNotes(),
                buildSteps(flow),
                flow.getCreatedAt()
        );
    }

    private static List<FlowStepResponse> buildSteps(SmithManeuverFlow flow) {
        var steps = new ArrayList<FlowStepResponse>();
        for (var step : flow.getSteps()) {
            if (step.getCashTransaction() != null) {
                steps.add(cashStep(step.getStepOrder(), step.getCashTransaction()));
            } else if (step.getSecurityTransaction() != null) {
                steps.add(securityStep(step.getStepOrder(), step.getSecurityTransaction()));
            }
        }
        return steps;
    }

    private static FlowStepResponse cashStep(int order, CashTransaction cash) {
        return new FlowStepResponse(
                order,
                "CASH",
                cashStepLabel(cash.getType().name()),
                cash.getAmount().abs(),
                null,
                cash.getPurpose() != null ? cash.getPurpose().name() : null,
                transferDetail(cash),
                cash.getId(),
                null
        );
    }

    private static FlowStepResponse securityStep(int order, SecurityTransaction security) {
        return new FlowStepResponse(
                order,
                "SECURITY",
                securityStepLabel(security.getAction()),
                null,
                security.getSecurity().getTicker(),
                null,
                tradeDetail(security),
                null,
                security.getId()
        );
    }

    /** Direction of a transfer leg, derived from the sign of the stored amount. */
    private static String transferDetail(CashTransaction cash) {
        if (cash.getCounterpartyAccount() == null) {
            return null;
        }
        boolean outflow = cash.getAmount().signum() < 0;
        var from = outflow ? cash.getAccount() : cash.getCounterpartyAccount();
        var to = outflow ? cash.getCounterpartyAccount() : cash.getAccount();
        return from.getLabel() + " \u2192 " + to.getLabel();
    }

    private static String tradeDetail(SecurityTransaction security) {
        if (security.getShares() == null || security.getPricePerShare() == null) {
            return null;
        }
        return trim(security.getShares()) + " @ " + trim(security.getPricePerShare());
    }

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String cashStepLabel(String type) {
        return switch (type) {
            case "HELOC_DRAW" -> "HELOC Draw";
            case "HELOC_REPAYMENT" -> "HELOC Repayment";
            case "TRANSFER" -> "Transfer";
            case "DEPOSIT" -> "Deposit";
            case "WITHDRAWAL" -> "Withdrawal";
            case "INTEREST_CHARGE", "INTEREST_PAYMENT" -> "Interest";
            case "FEE" -> "Fee";
            default -> type;
        };
    }

    private static String securityStepLabel(Action action) {
        return switch (action) {
            case BUY -> "Buy";
            case SELL -> "Sell";
            case RETURN_OF_CAPITAL -> "Return of Capital";
            case REINVESTED_DISTRIBUTION -> "Reinvested Dist.";
            case SPLIT -> "Split";
        };
    }
}
