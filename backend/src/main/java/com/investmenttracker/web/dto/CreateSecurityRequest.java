package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateSecurity")
public record CreateSecurityRequest(
        @NotBlank @Size(max = 32) String ticker,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 16) String assetClass,
        @Size(min = 3, max = 3) String currency
) {
}
