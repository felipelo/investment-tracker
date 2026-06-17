package com.investmenttracker.web.dto;

import com.investmenttracker.acb.HoldingSummary;
import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.domain.Security;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Schema(name = "Holding")
public record HoldingResponse(
        Long securityId,
        String ticker,
        String name,
        BigDecimal shareBalance,
        BigDecimal acbPerShare,
        BigDecimal totalAcb,
        BigDecimal latestPrice,
        LocalDate priceDate,
        BigDecimal marketValue,
        BigDecimal unrealizedGainLoss
) {
    public static HoldingResponse from(Security security, HoldingSummary summary, PriceSnapshot latestPrice) {
        BigDecimal price = latestPrice != null ? latestPrice.getPrice() : null;
        LocalDate priceDate = latestPrice != null ? latestPrice.getSnapshotDate() : null;
        BigDecimal marketValue = price != null
                ? summary.shareBalance().multiply(price).setScale(4, RoundingMode.HALF_UP)
                : null;
        BigDecimal unrealizedGainLoss = marketValue != null
                ? marketValue.subtract(summary.totalAcb()).setScale(4, RoundingMode.HALF_UP)
                : null;

        return new HoldingResponse(
                security.getId(),
                security.getTicker(),
                security.getName(),
                summary.shareBalance(),
                summary.acbPerShare(),
                summary.totalAcb(),
                price,
                priceDate,
                marketValue,
                unrealizedGainLoss
        );
    }
}
