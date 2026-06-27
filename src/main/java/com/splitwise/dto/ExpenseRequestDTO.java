package com.splitwise.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import com.splitwise.entity.SplitType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseRequestDTO {
    private String description;
    private BigDecimal amount;
    private Long paidByUserId;
    private Long groupId;
    private SplitType splitType;
    private List<SplitRequestDTO> splits;
}
