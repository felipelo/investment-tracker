package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Dividend;
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
        String notes,
        Instant createdAt
) {
    public static DividendResponse from(Dividend dividend) {
        var security = dividend.getSecurity();
        var account = dividend.getAccount();
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
                dividend.getNotes(),
                dividend.getCreatedAt()
        );
    }
}
