package com.investmenttracker.acb;

import com.investmenttracker.domain.Action;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Minimal transaction fields required for ACB computation. Callers map from
 * {@link com.investmenttracker.domain.SecurityTransaction} or test fixtures.
 */
public record SecurityTransactionInput(
        long id,
        LocalDate date,
        Action action,
        BigDecimal shares,
        BigDecimal pricePerShare,
        BigDecimal commission,
        BigDecimal cashAmount,
        BigDecimal splitRatio,
        BigDecimal deniedLossAdjustment,
        String notes
) {
    public SecurityTransactionInput {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long id;
        private LocalDate date;
        private Action action;
        private BigDecimal shares;
        private BigDecimal pricePerShare;
        private BigDecimal commission;
        private BigDecimal cashAmount;
        private BigDecimal splitRatio;
        private BigDecimal deniedLossAdjustment;
        private String notes;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder date(LocalDate date) {
            this.date = date;
            return this;
        }

        public Builder action(Action action) {
            this.action = action;
            return this;
        }

        public Builder shares(BigDecimal shares) {
            this.shares = shares;
            return this;
        }

        public Builder shares(long shares) {
            return shares(BigDecimal.valueOf(shares));
        }

        public Builder pricePerShare(BigDecimal pricePerShare) {
            this.pricePerShare = pricePerShare;
            return this;
        }

        public Builder pricePerShare(String pricePerShare) {
            return pricePerShare(new BigDecimal(pricePerShare));
        }

        public Builder commission(BigDecimal commission) {
            this.commission = commission;
            return this;
        }

        public Builder commission(String commission) {
            return commission(new BigDecimal(commission));
        }

        public Builder cashAmount(BigDecimal cashAmount) {
            this.cashAmount = cashAmount;
            return this;
        }

        public Builder cashAmount(long cashAmount) {
            return cashAmount(BigDecimal.valueOf(cashAmount));
        }

        public Builder splitRatio(BigDecimal splitRatio) {
            this.splitRatio = splitRatio;
            return this;
        }

        public Builder splitRatio(String splitRatio) {
            return splitRatio(new BigDecimal(splitRatio));
        }

        public Builder deniedLossAdjustment(BigDecimal deniedLossAdjustment) {
            this.deniedLossAdjustment = deniedLossAdjustment;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public SecurityTransactionInput build() {
            return new SecurityTransactionInput(
                    id,
                    date,
                    action,
                    shares,
                    pricePerShare,
                    commission,
                    cashAmount,
                    splitRatio,
                    deniedLossAdjustment,
                    notes
            );
        }
    }
}
