package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "Quote")
public record QuoteResponse(
        String symbol,
        @Schema(description = "Live (delayed) price; null when the symbol could not be fetched")
        BigDecimal price,
        @Schema(description = "Native currency of the quote; null when unavailable")
        String currency,
        Instant asOf,
        @Schema(description = "False when the symbol could not be fetched (price/currency are null)")
        boolean available
) {
    public static QuoteResponse of(String symbol, BigDecimal price, String currency) {
        return new QuoteResponse(symbol, price, currency, Instant.now(), true);
    }

    public static QuoteResponse unavailable(String symbol) {
        return new QuoteResponse(symbol, null, null, Instant.now(), false);
    }
}
