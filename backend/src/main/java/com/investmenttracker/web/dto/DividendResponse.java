package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.SecurityTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "Dividend")
public record DividendResponse(
        Long id,
        Long portfolioId,
        Long securityId,
        String ticker,
        String name,
        Long accountId,
        String accountLabel,
        LocalDate paymentDate,
        BigDecimal grossAmount,
        BigDecimal withholdingTax,
        BigDecimal netAmount,
        String currency,
        boolean drip,
        Long reinvestmentTransactionId,
        String reinvestmentTransactionLabel,
        String notes,
        Instant createdAt
) {
    public static DividendResponse from(Dividend dividend) {
        var security = dividend.getSecurity();
        var account = dividend.getAccount();
        var reinvestment = dividend.getReinvestmentTransaction();
        return new DividendResponse(
                dividend.getId(),
                dividend.getPortfolio().getId(),
                security.getId(),
                security.getTicker(),
                security.getName(),
                account != null ? account.getId() : null,
                account != null ? account.getLabel() : null,
                dividend.getPaymentDate(),
                dividend.getGrossAmount(),
                dividend.getWithholdingTax(),
                dividend.getNetAmount(),
                dividend.getCurrency(),
                dividend.isDrip(),
                reinvestment != null ? reinvestment.getId() : null,
                reinvestment != null ? describe(reinvestment) : null,
                dividend.getNotes(),
                dividend.getCreatedAt()
        );
    }

    private static String describe(SecurityTransaction transaction) {
        var label = new StringBuilder(actionLabel(transaction));
        if (transaction.getShares() != null && transaction.getPricePerShare() != null) {
            label.append(' ').append(plain(transaction.getShares()))
                    .append(" @ ").append(plain(transaction.getPricePerShare()));
        } else if (transaction.getCashAmount() != null) {
            label.append(' ').append(plain(transaction.getCashAmount()));
        }
        return label.append(" on ").append(transaction.getDate()).toString();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String actionLabel(SecurityTransaction transaction) {
        return switch (transaction.getAction()) {
            case BUY -> "Buy";
            case SELL -> "Sell";
            case RETURN_OF_CAPITAL -> "Return of Capital";
            case REINVESTED_DISTRIBUTION -> "Reinvested Distribution";
            case SPLIT -> "Split";
        };
    }
}
