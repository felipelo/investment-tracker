package com.investmenttracker.service;

import com.investmenttracker.domain.Security;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.web.dto.CreateSecurityRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Transactional
public class SecurityService {

    private final SecurityRepository securityRepository;

    public SecurityService(SecurityRepository securityRepository) {
        this.securityRepository = securityRepository;
    }

    public Security create(CreateSecurityRequest request) {
        var ticker = request.ticker().trim();
        var currency = request.currency() == null || request.currency().isBlank()
                ? "CAD"
                : request.currency().trim().toUpperCase();

        if (securityRepository.existsByTicker(ticker)) {
            throw new ValidationException(Map.of("ticker", "Ticker already exists"));
        }

        var security = new Security();
        security.setTicker(ticker);
        security.setName(request.name());
        security.setAssetClass(blankToNull(request.assetClass()));
        security.setCurrency(currency);

        return securityRepository.save(security);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
