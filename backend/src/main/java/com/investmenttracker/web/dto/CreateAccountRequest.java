package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateAccount")
public record CreateAccountRequest(
        @NotBlank @Size(max = 128) String label,
        @NotBlank @Size(max = 24) String type,
        @Size(min = 3, max = 3) String currency
) {
}
