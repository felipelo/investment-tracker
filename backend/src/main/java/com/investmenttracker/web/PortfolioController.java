package com.investmenttracker.web;

import com.investmenttracker.service.PortfolioService;
import com.investmenttracker.web.dto.CreatePortfolioRequest;
import com.investmenttracker.web.dto.PortfolioResponse;
import com.investmenttracker.web.dto.UpdatePortfolioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    @Operation(summary = "List portfolios with derived value, return and holdings count")
    public List<PortfolioResponse> listPortfolios() {
        return portfolioService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single portfolio with derived metrics")
    public PortfolioResponse getPortfolio(@PathVariable Long id) {
        return portfolioService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a portfolio")
    public PortfolioResponse createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
        return portfolioService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a portfolio")
    public PortfolioResponse updatePortfolio(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePortfolioRequest request
    ) {
        return portfolioService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a portfolio (only when it has no accounts)")
    public void deletePortfolio(@PathVariable Long id) {
        portfolioService.delete(id);
    }
}
