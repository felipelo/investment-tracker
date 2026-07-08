package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreatePortfolioSnapshot")
public record CreatePortfolioSnapshotRequest(
        @NotNull LocalDate date,
        @NotNull @DecimalMin(value = "0.0", message = "Market value must be zero or greater") BigDecimal marketValue
) {
}
