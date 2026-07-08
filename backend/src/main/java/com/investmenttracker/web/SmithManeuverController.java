package com.investmenttracker.web;

import com.investmenttracker.service.SmithManeuverFlowService;
import com.investmenttracker.service.SmithManeuverService;
import com.investmenttracker.web.dto.CreateSmithManeuverFlowRequest;
import com.investmenttracker.web.dto.SmithManeuverFlowResponse;
import com.investmenttracker.web.dto.SmithManeuverResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Smith Maneuver")
public class SmithManeuverController {

    private final SmithManeuverService smithManeuverService;
    private final SmithManeuverFlowService flowService;

    public SmithManeuverController(
            SmithManeuverService smithManeuverService,
            SmithManeuverFlowService flowService
    ) {
        this.smithManeuverService = smithManeuverService;
        this.flowService = flowService;
    }

    @GetMapping("/api/v1/portfolios/{portfolioId}/smith-maneuver")
    @Operation(summary = "Aggregated Smith Maneuver view: investment-use balance, flows, HELOC accounts, interest log")
    public SmithManeuverResponse getSmithManeuver(@PathVariable Long portfolioId) {
        return smithManeuverService.getSmithManeuver(portfolioId);
    }

    @PostMapping("/api/v1/smith-maneuver-flows")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a borrow-to-invest flow chain")
    public SmithManeuverFlowResponse createFlow(@Valid @RequestBody CreateSmithManeuverFlowRequest request) {
        return flowService.create(request);
    }

    @PutMapping("/api/v1/smith-maneuver-flows/{id}")
    @Operation(summary = "Update a flow chain")
    public SmithManeuverFlowResponse updateFlow(
            @PathVariable Long id,
            @Valid @RequestBody CreateSmithManeuverFlowRequest request
    ) {
        return flowService.update(id, request);
    }

    @DeleteMapping("/api/v1/smith-maneuver-flows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a flow chain")
    public void deleteFlow(@PathVariable Long id) {
        flowService.delete(id);
    }
}
