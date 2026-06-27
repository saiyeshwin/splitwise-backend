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
public class SplitRequestDTO {
    private Long userId;
    private BigDecimal value; // Represents split amount for EXACT or percentage for PERCENT
}
