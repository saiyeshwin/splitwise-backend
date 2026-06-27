package com.splitwise.repository;

import com.splitwise.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.Expense;
import java.util.List;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit,Long>{
    List<ExpenseSplit> findByExpense(Expense expense);
}