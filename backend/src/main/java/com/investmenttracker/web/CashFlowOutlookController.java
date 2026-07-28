package com.investmenttracker.web;

import com.investmenttracker.service.CashFlowOutlookService;
import com.investmenttracker.web.dto.CashFlowOutlookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Cash flow outlook")
public class CashFlowOutlookController {

    private final CashFlowOutlookService cashFlowOutlookService;

    public CashFlowOutlookController(CashFlowOutlookService cashFlowOutlookService) {
        this.cashFlowOutlookService = cashFlowOutlookService;
    }

    @GetMapping("/api/v1/cash-flow-outlook")
    @Operation(summary = "Projected dividends in versus fees and interest out over the next 8 weeks")
    public CashFlowOutlookResponse getCashFlowOutlook(
            @RequestParam(required = false) Long portfolioId
    ) {
        return cashFlowOutlookService.getOutlook(portfolioId);
    }
}
