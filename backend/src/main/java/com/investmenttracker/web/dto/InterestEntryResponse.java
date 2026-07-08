package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "SmithManeuverInterestEntry")
public record InterestEntryResponse(
        Long id,
        LocalDate date,
        String type,
        BigDecimal amount,
        BigDecimal deductibleEstimate
) {
}
