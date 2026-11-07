package com.xuelvquan.travel.repository;

import com.xuelvquan.travel.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Expense> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<Expense> findByScopeAndUserIdOrderByCreatedAtDesc(String scope, Long userId);

    List<Expense> findByScopeAndTripIdOrderByCreatedAtDesc(String scope, Long tripId);
}
