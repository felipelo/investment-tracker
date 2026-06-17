package com.investmenttracker.web.dto;

import com.investmenttracker.domain.PriceSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "PriceSnapshot")
public record PriceSnapshotResponse(
        Long id,
        Long securityId,
        LocalDate date,
        BigDecimal price,
        Instant createdAt
) {
    public static PriceSnapshotResponse from(PriceSnapshot snapshot) {
        return new PriceSnapshotResponse(
                snapshot.getId(),
                snapshot.getSecurity().getId(),
                snapshot.getSnapshotDate(),
                snapshot.getPrice(),
                snapshot.getCreatedAt()
        );
    }
}
