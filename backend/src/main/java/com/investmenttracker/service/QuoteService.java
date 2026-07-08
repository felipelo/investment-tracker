package com.investmenttracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.investmenttracker.web.dto.QuoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final RestClient client;
    private final String apiKey;
    private final Duration cacheTtl;
    private final Duration requestGap;

    private final ConcurrentHashMap<String, CachedQuote> cache = new ConcurrentHashMap<>();
    private final Object fetchLock = new Object();
    private Instant lastFetchAt = Instant.EPOCH;

    public QuoteService(
            RestClient alphaVantageRestClient,
            @Value("${marketdata.alpha-vantage.api-key:}") String apiKey,
            @Value("${marketdata.alpha-vantage.cache-ttl-seconds:60}") long cacheTtlSeconds,
            @Value("${marketdata.alpha-vantage.request-gap-ms:1100}") long requestGapMs) {
        this.client = alphaVantageRestClient;
        this.apiKey = apiKey;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        this.requestGap = Duration.ofMillis(requestGapMs);
    }

    /**
     * Fetches a live (delayed) price per symbol. Each symbol is fetched
     * independently so one bad ticker yields an {@code available=false} entry
     * rather than failing the whole request.
     */
    public List<QuoteResponse> getQuotes(List<String> symbols) {
        if (apiKey.isBlank()) {
            log.warn("Alpha Vantage API key is not configured (set ALPHAVANTAGE_API_KEY); "
                    + "live quotes are unavailable");
        }

        var unique = new LinkedHashSet<String>();
        for (var raw : symbols) {
            if (raw == null) {
                continue;
            }
            var symbol = raw.trim().toUpperCase();
            if (!symbol.isEmpty()) {
                unique.add(symbol);
            }
        }

        var quotes = new ArrayList<QuoteResponse>(unique.size());
        for (var symbol : unique) {
            var cached = getCached(symbol);
            if (cached != null) {
                quotes.add(cached);
            } else {
                var quote = fetchOnce(symbol);
                if (quote.available()) {
                    cache.put(symbol, new CachedQuote(quote, Instant.now().plus(cacheTtl)));
                }
                quotes.add(quote);
            }
        }
        return quotes;
    }

    private QuoteResponse fetchOnce(String symbol) {
        if (apiKey.isBlank()) {
            return QuoteResponse.unavailable(symbol);
        }
        throttle();
        try {
            JsonNode body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            return parse(symbol, body);
        } catch (RuntimeException e) {
            log.warn("Live quote fetch failed for {}: {}", symbol, e.getMessage());
            return QuoteResponse.unavailable(symbol);
        }
    }

    private QuoteResponse parse(String symbol, JsonNode body) {
        if (body == null) {
            return QuoteResponse.unavailable(symbol);
        }
        // Alpha Vantage signals throttling / errors with these keys instead of data.
        if (body.hasNonNull("Note") || body.hasNonNull("Information")) {
            log.warn("Alpha Vantage throttled or limited the request for {}: {}",
                    symbol, body.path(body.has("Note") ? "Note" : "Information").asText());
            return QuoteResponse.unavailable(symbol);
        }
        if (body.hasNonNull("Error Message")) {
            log.warn("Alpha Vantage rejected symbol {}: {}", symbol,
                    body.path("Error Message").asText());
            return QuoteResponse.unavailable(symbol);
        }

        JsonNode quote = body.path("Global Quote");
        JsonNode priceNode = quote.path("05. price");
        if (!priceNode.isMissingNode() && !priceNode.asText().isBlank()) {
            try {
                BigDecimal price = new BigDecimal(priceNode.asText().trim());
                if (price.signum() > 0) {
                    return QuoteResponse.of(symbol, price, inferCurrency(symbol));
                }
            } catch (NumberFormatException e) {
                log.warn("Unparseable price '{}' for {}", priceNode.asText(), symbol);
            }
        }
        return QuoteResponse.unavailable(symbol);
    }

    private static String inferCurrency(String symbol) {
        if (symbol.endsWith(".TRT") || symbol.endsWith(".TRV")) {
            return "CAD";
        }
        return "USD";
    }

    private QuoteResponse getCached(String symbol) {
        var entry = cache.get(symbol);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(symbol, entry);
            return null;
        }
        return entry.response();
    }

    private void throttle() {
        if (requestGap.isZero() || requestGap.isNegative()) {
            return;
        }
        synchronized (fetchLock) {
            var now = Instant.now();
            var sinceLast = Duration.between(lastFetchAt, now);
            if (sinceLast.compareTo(requestGap) < 0) {
                sleep(requestGap.minus(sinceLast));
            }
            lastFetchAt = Instant.now();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between quote requests", e);
        }
    }

    private record CachedQuote(QuoteResponse response, Instant expiresAt) {
    }
}
