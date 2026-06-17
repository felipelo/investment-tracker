package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreatePortfolio")
public record CreatePortfolioRequest(
        @NotBlank @Size(max = 128) String name,
        String description,
        @Size(min = 3, max = 3) String baseCurrency,
        @Size(max = 24) String type
) {
}
