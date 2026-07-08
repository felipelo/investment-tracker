package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SmithManeuverWarning")
public record SmithManeuverWarningResponse(
        String title,
        String detail
) {
}
