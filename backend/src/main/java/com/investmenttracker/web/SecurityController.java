package com.investmenttracker.web;

import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.service.SecurityService;
import com.investmenttracker.web.dto.CreateSecurityRequest;
import com.investmenttracker.web.dto.SecurityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Tag(name = "Securities")
public class SecurityController {

    private final SecurityRepository securityRepository;
    private final SecurityService securityService;

    public SecurityController(SecurityRepository securityRepository, SecurityService securityService) {
        this.securityRepository = securityRepository;
        this.securityService = securityService;
    }

    @GetMapping
    @Operation(summary = "List securities (dropdown lookup)")
    public List<SecurityResponse> listSecurities() {
        return securityRepository.findAllByOrderByTickerAsc().stream()
                .map(SecurityResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a security")
    public SecurityResponse createSecurity(@Valid @RequestBody CreateSecurityRequest request) {
        return SecurityResponse.from(securityService.create(request));
    }
}
