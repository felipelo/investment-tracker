package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "CreatePriceSnapshots")
public record CreatePriceSnapshotsRequest(
        @NotEmpty @Valid List<Snapshot> snapshots
) {
    @Schema(name = "CreatePriceSnapshotItem")
    public record Snapshot(
            @NotNull Long securityId,
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.0", message = "Price must be zero or greater") BigDecimal price
    ) {
    }
}
