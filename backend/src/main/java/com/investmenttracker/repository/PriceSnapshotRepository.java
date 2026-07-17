package com.investmenttracker.repository;

import com.investmenttracker.domain.PriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    @Query("""
            SELECT p FROM PriceSnapshot p
            JOIN FETCH p.security
            WHERE (:securityId IS NULL OR p.security.id = :securityId)
            ORDER BY p.snapshotDate DESC, p.id DESC
            """)
    List<PriceSnapshot> findFiltered(@Param("securityId") Long securityId);

    Optional<PriceSnapshot> findBySecurityIdAndSnapshotDate(Long securityId, LocalDate snapshotDate);

    @Query("""
            SELECT p FROM PriceSnapshot p
            JOIN FETCH p.security
            WHERE p.snapshotDate = (
                SELECT MAX(p2.snapshotDate) FROM PriceSnapshot p2 WHERE p2.security.id = p.security.id
            )
            """)
    List<PriceSnapshot> findLatestPerSecurity();

    @Query("""
            SELECT p FROM PriceSnapshot p
            JOIN FETCH p.security
            WHERE p.snapshotDate = (
                SELECT MAX(p2.snapshotDate) FROM PriceSnapshot p2
                WHERE p2.security.id = p.security.id AND p2.snapshotDate <= :asOf
            )
            """)
    List<PriceSnapshot> findLatestPerSecurityAsOf(@Param("asOf") LocalDate asOf);
}
