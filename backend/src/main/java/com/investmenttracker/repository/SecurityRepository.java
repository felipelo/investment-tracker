package com.investmenttracker.repository;

import com.investmenttracker.domain.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityRepository extends JpaRepository<Security, Long> {

    List<Security> findAllByOrderByTickerAsc();

    boolean existsByTicker(String ticker);
}
