package com.investmenttracker.web;

import com.investmenttracker.service.HoldingService;
import com.investmenttracker.web.dto.HoldingHistoryRowResponse;
import com.investmenttracker.web.dto.HoldingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holdings")
@Tag(name = "Holdings")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping
    @Operation(summary = "List current holdings with computed ACB for a portfolio")
    public List<HoldingResponse> listHoldings(@RequestParam Long portfolioId) {
        return holdingService.listHoldings(portfolioId);
    }

    @GetMapping("/{securityId}/history")
    @Operation(summary = "Get computed transaction history for a security in a portfolio")
    public List<HoldingHistoryRowResponse> getHistory(
            @PathVariable Long securityId,
            @RequestParam Long portfolioId
    ) {
        return holdingService.getHistory(portfolioId, securityId);
    }
}
