package com.investmenttracker.service;

import com.investmenttracker.domain.PriceSnapshot;
import com.investmenttracker.repository.PriceSnapshotRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.web.dto.CreatePriceSnapshotsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional
public class PriceSnapshotService {

    private final PriceSnapshotRepository priceSnapshotRepository;
    private final SecurityRepository securityRepository;

    public PriceSnapshotService(
            PriceSnapshotRepository priceSnapshotRepository,
            SecurityRepository securityRepository
    ) {
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.securityRepository = securityRepository;
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
        for (var item : request.snapshots()) {
            var snapshot = priceSnapshotRepository
                    .findBySecurityIdAndSnapshotDate(item.securityId(), item.date())
                    .orElseGet(PriceSnapshot::new);
            snapshot.setSecurity(securityRepository.getReferenceById(item.securityId()));
            snapshot.setSnapshotDate(item.date());
            snapshot.setPrice(item.price());
            saved.add(priceSnapshotRepository.save(snapshot));
        }
        return saved;
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
