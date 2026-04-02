package com.bloomberg.fxdeals.repository;

import com.bloomberg.fxdeals.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {
    Optional<Deal> findByDealUniqueId(String dealUniqueId);
    boolean existsByDealUniqueId(String dealUniqueId);
}
