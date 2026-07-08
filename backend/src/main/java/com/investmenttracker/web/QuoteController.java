package com.investmenttracker.web;

import com.investmenttracker.service.QuoteService;
import com.investmenttracker.web.dto.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes")
@Validated
@Tag(name = "Quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    @Operation(summary = "Fetch live (delayed) prices for one or more market ticker symbols")
    public List<QuoteResponse> getQuotes(@RequestParam @NotEmpty List<String> symbols) {
        return quoteService.getQuotes(symbols);
    }
}
