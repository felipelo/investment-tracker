package com.investmenttracker.web;

import com.investmenttracker.service.PriceSnapshotService;
import com.investmenttracker.web.dto.CreatePriceSnapshotsRequest;
import com.investmenttracker.web.dto.PriceSnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/price-snapshots")
@Tag(name = "Price Snapshots")
public class PriceSnapshotController {

    private final PriceSnapshotService priceSnapshotService;

    public PriceSnapshotController(PriceSnapshotService priceSnapshotService) {
        this.priceSnapshotService = priceSnapshotService;
    }

    @GetMapping
    @Operation(summary = "List price snapshots")
    public List<PriceSnapshotResponse> listSnapshots(@RequestParam(required = false) Long securityId) {
        return priceSnapshotService.list(securityId).stream()
                .map(PriceSnapshotResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record one or more price snapshots (batch upsert per security/date)")
    public List<PriceSnapshotResponse> createSnapshots(
            @Valid @RequestBody CreatePriceSnapshotsRequest request
    ) {
        return priceSnapshotService.createBatch(request).stream()
                .map(PriceSnapshotResponse::from)
                .toList();
    }
}
