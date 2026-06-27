package com.splitwise.controller;

import com.splitwise.dto.ExpenseRequestDTO;
import com.splitwise.dto.CreateExpenseResponseDTO;
import com.splitwise.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {
    private final ExpenseService expenseService;
    @PostMapping
    public CreateExpenseResponseDTO createExpense(@RequestBody ExpenseRequestDTO requestDTO){
        return expenseService.createExpense(requestDTO);
    }
}