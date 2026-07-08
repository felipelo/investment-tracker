package com.investmenttracker.service;

import com.investmenttracker.web.dto.QuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QuoteServiceTest {

    private record Fixture(QuoteService service, MockRestServiceServer server) {
    }

    private Fixture fixtureWithKey(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.alphavantage.co");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // request-gap-ms = 0 keeps the test fast (no throttling sleep).
        QuoteService service = new QuoteService(builder.build(), apiKey, 60, 0);
        return new Fixture(service, server);
    }

    @Test
    void parsesUsQuoteInUsd() {
        var fx = fixtureWithKey("TESTKEY");
        fx.server().expect(requestTo(containsString("symbol=IBM")))
                .andRespond(withSuccess(
                        "{\"Global Quote\":{\"01. symbol\":\"IBM\",\"05. price\":\"249.1800\"}}",
                        MediaType.APPLICATION_JSON));

        List<QuoteResponse> quotes = fx.service().getQuotes(List.of("ibm"));

        assertThat(quotes).hasSize(1);
        QuoteResponse quote = quotes.get(0);
        assertThat(quote.symbol()).isEqualTo("IBM");
        assertThat(quote.available()).isTrue();
        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("249.18"));
        assertThat(quote.currency()).isEqualTo("USD");
        fx.server().verify();
    }

    @Test
    void infersCadForTsxSuffix() {
        var fx = fixtureWithKey("TESTKEY");
        fx.server().expect(requestTo(containsString("symbol=ENB.TRT")))
                .andRespond(withSuccess(
                        "{\"Global Quote\":{\"01. symbol\":\"ENB.TRT\",\"05. price\":\"76.9000\"}}",
                        MediaType.APPLICATION_JSON));

        QuoteResponse quote = fx.service().getQuotes(List.of("ENB.TRT")).get(0);

        assertThat(quote.available()).isTrue();
        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("76.90"));
        assertThat(quote.currency()).isEqualTo("CAD");
        fx.server().verify();
    }

    @Test
    void unknownSymbolReturnsUnavailable() {
        var fx = fixtureWithKey("TESTKEY");
        fx.server().expect(requestTo(containsString("symbol=FAKEZZZ")))
                .andRespond(withSuccess("{\"Global Quote\":{}}", MediaType.APPLICATION_JSON));

        QuoteResponse quote = fx.service().getQuotes(List.of("FAKEZZZ")).get(0);

        assertThat(quote.available()).isFalse();
        assertThat(quote.price()).isNull();
        fx.server().verify();
    }

    @Test
    void rateLimitResponseReturnsUnavailable() {
        var fx = fixtureWithKey("TESTKEY");
        fx.server().expect(requestTo(containsString("symbol=IBM")))
                .andRespond(withSuccess(
                        "{\"Information\":\"Thank you for using Alpha Vantage! "
                                + "the free key rate limit is 25 requests per day\"}",
                        MediaType.APPLICATION_JSON));

        QuoteResponse quote = fx.service().getQuotes(List.of("IBM")).get(0);

        assertThat(quote.available()).isFalse();
        fx.server().verify();
    }

    @Test
    void missingApiKeySkipsHttpCallAndReturnsUnavailable() {
        var fx = fixtureWithKey("");

        QuoteResponse quote = fx.service().getQuotes(List.of("IBM")).get(0);

        assertThat(quote.available()).isFalse();
        // No request expectations were set; verify confirms none were made.
        fx.server().verify();
    }
}
