package com.investmenttracker.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** Aggregated Smith Maneuver payload (REQUIREMENTS.md sections 3.10 and 6.4). */
@Schema(name = "SmithManeuver")
public record SmithManeuverResponse(
        BigDecimal investmentUseBalance,
        List<SmithManeuverFlowResponse> flows,
        List<HelocAccountSummaryResponse> helocAccounts,
        List<InterestEntryResponse> interestLog,
        List<SmithManeuverWarningResponse> warnings
) {
}
