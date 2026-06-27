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
public class BalanceResponseDTO {
    private Long debtorId;
    private String debtorName;
    private Long creditorId;
    private String creditorName;
    private BigDecimal amount;
}
