package com.investmenttracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One ordered link in a Smith Maneuver flow: either a cash transaction leg or the terminal buy. */
@Entity
@Table(name = "smith_maneuver_flow_step")
public class SmithManeuverFlowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "flow_id", nullable = false)
    private SmithManeuverFlow flow;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_transaction_id")
    private CashTransaction cashTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_transaction_id")
    private SecurityTransaction securityTransaction;

    public Long getId() {
        return id;
    }

    public SmithManeuverFlow getFlow() {
        return flow;
    }

    public void setFlow(SmithManeuverFlow flow) {
        this.flow = flow;
    }

    public int getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(int stepOrder) {
        this.stepOrder = stepOrder;
    }

    public CashTransaction getCashTransaction() {
        return cashTransaction;
    }

    public void setCashTransaction(CashTransaction cashTransaction) {
        this.cashTransaction = cashTransaction;
    }

    public SecurityTransaction getSecurityTransaction() {
        return securityTransaction;
    }

    public void setSecurityTransaction(SecurityTransaction securityTransaction) {
        this.securityTransaction = securityTransaction;
    }
}
