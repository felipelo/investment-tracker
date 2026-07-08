package com.investmenttracker.repository;

import com.investmenttracker.domain.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    List<PortfolioSnapshot> findByPortfolioIdOrderBySnapshotDateDesc(Long portfolioId);

    Optional<PortfolioSnapshot> findByPortfolioIdAndSnapshotDate(Long portfolioId, LocalDate snapshotDate);

    Optional<PortfolioSnapshot> findTopByPortfolioIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(
            Long portfolioId,
            LocalDate snapshotDate
    );
}
