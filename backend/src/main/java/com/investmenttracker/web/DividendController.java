package com.investmenttracker.web;

import com.investmenttracker.service.DividendService;
import com.investmenttracker.web.dto.CreateDividendRequest;
import com.investmenttracker.web.dto.DividendResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dividends")
@Tag(name = "Dividends")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @GetMapping
    @Operation(summary = "List dividends for a portfolio (most recent first)")
    public List<DividendResponse> listDividends(@RequestParam Long portfolioId) {
        return dividendService.list(portfolioId).stream()
                .map(DividendResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a dividend")
    public DividendResponse createDividend(@Valid @RequestBody CreateDividendRequest request) {
        return DividendResponse.from(dividendService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a dividend")
    public DividendResponse updateDividend(
            @PathVariable Long id,
            @Valid @RequestBody CreateDividendRequest request
    ) {
        return DividendResponse.from(dividendService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a dividend")
    public void deleteDividend(@PathVariable Long id) {
        dividendService.delete(id);
    }
}
