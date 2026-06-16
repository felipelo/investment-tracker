package com.investmenttracker.web.dto;

import com.investmenttracker.domain.Security;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Security")
public record SecurityResponse(
        Long id,
        String ticker,
        String name,
        String assetClass,
        String currency
) {
    public static SecurityResponse from(Security security) {
        return new SecurityResponse(
                security.getId(),
                security.getTicker(),
                security.getName(),
                security.getAssetClass(),
                security.getCurrency()
        );
    }
}
