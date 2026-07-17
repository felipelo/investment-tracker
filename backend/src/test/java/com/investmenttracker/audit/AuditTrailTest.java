package com.investmenttracker.audit;

import com.investmenttracker.domain.Action;
import com.investmenttracker.domain.Dividend;
import com.investmenttracker.domain.SecurityTransaction;
import com.investmenttracker.repository.AccountRepository;
import com.investmenttracker.repository.PortfolioRepository;
import com.investmenttracker.repository.SecurityRepository;
import com.investmenttracker.service.DividendService;
import com.investmenttracker.service.SecurityTransactionService;
import com.investmenttracker.web.dto.CreateDividendRequest;
import com.investmenttracker.web.dto.CreateSecurityTransactionRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditTrailTest {

    @Autowired
    private DividendService dividendService;

    @Autowired
    private SecurityTransactionService securityTransactionService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void dividendCreateUpdateDeleteProducesAddModDelRevisions() {
        var portfolioId = portfolioRepository.findAllByOrderByNameAsc().getFirst().getId();
        var securityId = securityRepository.findAllByOrderByTickerAsc().getFirst().getId();

        var created = dividendService.create(new CreateDividendRequest(
                portfolioId, securityId, null, LocalDate.parse("2026-01-15"),
                new BigDecimal("50"), BigDecimal.ZERO, null, false, null, "initial"));
        Long dividendId = created.getId();

        dividendService.update(dividendId, new CreateDividendRequest(
                portfolioId, securityId, null, LocalDate.parse("2026-01-15"),
                new BigDecimal("80"), BigDecimal.ZERO, null, false, null, "updated"));

        dividendService.delete(dividendId);

        var em = entityManagerFactory.createEntityManager();
        try {
            AuditReader reader = AuditReaderFactory.get(em);

            var revisions = reader.getRevisions(Dividend.class, dividendId);
            assertThat(revisions).hasSize(3);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = reader.createQuery()
                    .forRevisionsOfEntity(Dividend.class, false, true)
                    .add(AuditEntity.id().eq(dividendId))
                    .getResultList();

            assertThat(rows).hasSize(3);
            assertThat(rows).extracting(row -> row[2])
                    .containsExactly(RevisionType.ADD, RevisionType.MOD, RevisionType.DEL);

            // History survives the hard delete: the pre-delete (MOD) revision still
            // reconstructs the full prior state with the updated gross amount.
            var preDeleteRevision = revisions.get(1);
            var preDeleteSnapshot = reader.find(Dividend.class, dividendId, preDeleteRevision);
            assertThat(preDeleteSnapshot).isNotNull();
            assertThat(preDeleteSnapshot.getGrossAmount()).isEqualByComparingTo("80");
        } finally {
            em.close();
        }
    }

    @Test
    void securityTransactionCreateUpdateDeleteIsAudited() {
        var securityId = securityRepository.findAllByOrderByTickerAsc().getFirst().getId();
        var accountId = accountRepository.findAllByOrderByLabelAsc().getFirst().getId();

        var created = securityTransactionService.create(new CreateSecurityTransactionRequest(
                LocalDate.parse("2026-01-05"), securityId, accountId, Action.BUY,
                new BigDecimal("10"), new BigDecimal("20"), BigDecimal.ZERO, null, null, null, null));
        Long transactionId = created.getId();

        securityTransactionService.update(transactionId, new CreateSecurityTransactionRequest(
                LocalDate.parse("2026-01-05"), securityId, accountId, Action.BUY,
                new BigDecimal("15"), new BigDecimal("20"), BigDecimal.ZERO, null, null, null, null));

        securityTransactionService.delete(transactionId);

        var em = entityManagerFactory.createEntityManager();
        try {
            AuditReader reader = AuditReaderFactory.get(em);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = reader.createQuery()
                    .forRevisionsOfEntity(SecurityTransaction.class, false, true)
                    .add(AuditEntity.id().eq(transactionId))
                    .getResultList();

            assertThat(rows).hasSize(3);
            assertThat(rows).extracting(row -> row[2])
                    .containsExactly(RevisionType.ADD, RevisionType.MOD, RevisionType.DEL);
        } finally {
            em.close();
        }
    }
}
