package com.investmenttracker.web;

import com.investmenttracker.service.CashTransactionService;
import com.investmenttracker.web.dto.CashTransactionResponse;
import com.investmenttracker.web.dto.CreateCashTransactionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-transactions")
@Tag(name = "Cash transactions")
public class CashTransactionController {

    private final CashTransactionService cashTransactionService;

    public CashTransactionController(CashTransactionService cashTransactionService) {
        this.cashTransactionService = cashTransactionService;
    }

    @GetMapping
    @Operation(summary = "List cash transactions for a portfolio (most recent first)")
    public List<CashTransactionResponse> listCashTransactions(@RequestParam Long portfolioId) {
        return cashTransactionService.listWithBalances(portfolioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a cash transaction")
    public CashTransactionResponse createCashTransaction(
            @Valid @RequestBody CreateCashTransactionRequest request
    ) {
        return CashTransactionResponse.from(cashTransactionService.create(request), null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a cash transaction")
    public CashTransactionResponse updateCashTransaction(
            @PathVariable Long id,
            @Valid @RequestBody CreateCashTransactionRequest request
    ) {
        return CashTransactionResponse.from(cashTransactionService.update(id, request), null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a cash transaction")
    public void deleteCashTransaction(@PathVariable Long id) {
        cashTransactionService.delete(id);
    }
}
