package com.investmenttracker.service;

import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.repository.PriceSnapshotRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.web.dto.CreatePriceSnapshotsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Transactional
public class PriceSnapshotService {

    private final PriceSnapshotRepository priceSnapshotRepository;
    private final SecurityRepository securityRepository;
    private final PortfolioSnapshotService portfolioSnapshotService;

    public PriceSnapshotService(
            PriceSnapshotRepository priceSnapshotRepository,
            SecurityRepository securityRepository,
            PortfolioSnapshotService portfolioSnapshotService
    ) {
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.securityRepository = securityRepository;
        this.portfolioSnapshotService = portfolioSnapshotService;
    }

    @Transactional(readOnly = true)
    public List<PriceSnapshot> list(Long securityId) {
        return priceSnapshotRepository.findFiltered(securityId);
    }

    /**
     * Upserts one snapshot per (security, date): a re-submitted date overwrites
     * the prior price rather than violating the unique constraint.
     */
    public List<PriceSnapshot> createBatch(CreatePriceSnapshotsRequest request) {
        validate(request);

        var saved = new ArrayList<PriceSnapshot>(request.snapshots().size());
        var securityIds = new LinkedHashSet<Long>();
        LocalDate maxDate = null;
        for (var item : request.snapshots()) {
            var snapshot = priceSnapshotRepository
                    .findBySecurityIdAndSnapshotDate(item.securityId(), item.date())
                    .orElseGet(PriceSnapshot::new);
            snapshot.setSecurity(securityRepository.getReferenceById(item.securityId()));
            snapshot.setSnapshotDate(item.date());
            snapshot.setPrice(item.price());
            saved.add(priceSnapshotRepository.save(snapshot));
            securityIds.add(item.securityId());
            if (maxDate == null || item.date().isAfter(maxDate)) {
                maxDate = item.date();
            }
        }

        // Auto-capture a portfolio_snapshot at the batch's effective date so the
        // dashboard's today's/period returns have a stored value to compare against.
        capturePortfolioSnapshots(securityIds, maxDate);
        return saved;
    }

    private void capturePortfolioSnapshots(Set<Long> securityIds, LocalDate date) {
        portfolioSnapshotService.captureForSecurities(securityIds, date);
    }

    private void validate(CreatePriceSnapshotsRequest request) {
        var errors = new LinkedHashMap<String, String>();
        for (int i = 0; i < request.snapshots().size(); i++) {
            var item = request.snapshots().get(i);
            if (!securityRepository.existsById(item.securityId())) {
                errors.put("snapshots[" + i + "].securityId", "Security not found");
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
