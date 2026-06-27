package com.splitwise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequestDTO {
    private Long groupId;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
}
