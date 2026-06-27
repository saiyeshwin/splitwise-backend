package com.splitwise.repository;

import com.splitwise.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpensesRepository extends JpaRepository<Expense,Long> {
    List<Expense> findByGroupId(Long groupId);
}
