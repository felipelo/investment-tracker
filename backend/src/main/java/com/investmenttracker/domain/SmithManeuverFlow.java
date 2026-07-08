package com.investmenttracker.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "smith_maneuver_flow")
public class SmithManeuverFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "heloc_account_id", nullable = false)
    private Account helocAccount;

    @Column(nullable = false, length = 160)
    private String label;

    @Column(name = "investment_use_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal investmentUseAmount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<SmithManeuverFlowStep> steps = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addStep(SmithManeuverFlowStep step) {
        step.setFlow(this);
        steps.add(step);
    }

    public void clearSteps() {
        steps.clear();
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public Account getHelocAccount() {
        return helocAccount;
    }

    public void setHelocAccount(Account helocAccount) {
        this.helocAccount = helocAccount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getInvestmentUseAmount() {
        return investmentUseAmount;
    }

    public void setInvestmentUseAmount(BigDecimal investmentUseAmount) {
        this.investmentUseAmount = investmentUseAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<SmithManeuverFlowStep> getSteps() {
        return steps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
