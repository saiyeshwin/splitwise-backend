package com.splitwise.dto;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateExpenseResponseDTO {
    private Long expenseId;
    private String description;
    private BigDecimal amount;
    private String paidBy;
    private String groupName;
}