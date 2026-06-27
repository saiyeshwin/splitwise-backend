package com.splitwise.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class CreateExpenseRequestDTO {
    private String description;
    private BigDecimal amount;
    private Long paidById;
    private Long groupId;
}