package com.investmenttracker.repository;

import com.investmenttracker.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByOrderByLabelAsc();

    List<Account> findByPortfolioIdOrderByLabelAsc(Long portfolioId);

    boolean existsByPortfolioId(Long portfolioId);
}
