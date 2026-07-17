package com.investmenttracker.web;

import com.investmenttracker.service.SecurityTransactionService;
import com.investmenttracker.web.dto.CreateSecurityTransactionRequest;
import com.investmenttracker.web.dto.SecurityTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/security-transactions")
@Tag(name = "Security Transactions")
public class SecurityTransactionController {

    private final SecurityTransactionService securityTransactionService;

    public SecurityTransactionController(SecurityTransactionService securityTransactionService) {
        this.securityTransactionService = securityTransactionService;
    }

    @GetMapping
    @Operation(summary = "List security transactions")
    public List<SecurityTransactionResponse> listTransactions(
            @RequestParam(required = false) Long portfolioId,
            @RequestParam(required = false) Long securityId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return securityTransactionService.list(portfolioId, securityId, accountId, from, to).stream()
                .map(SecurityTransactionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a security transaction by id")
    public SecurityTransactionResponse getTransaction(@PathVariable Long id) {
        return SecurityTransactionResponse.from(securityTransactionService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a security transaction")
    public SecurityTransactionResponse createTransaction(
            @Valid @RequestBody CreateSecurityTransactionRequest request
    ) {
        return SecurityTransactionResponse.from(securityTransactionService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a security transaction")
    public SecurityTransactionResponse updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody CreateSecurityTransactionRequest request
    ) {
        return SecurityTransactionResponse.from(securityTransactionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a security transaction")
    public void deleteTransaction(@PathVariable Long id) {
        securityTransactionService.delete(id);
    }
}
