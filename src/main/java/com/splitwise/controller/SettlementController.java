package com.splitwise.controller;

import com.splitwise.dto.SettlementRequestDTO;
import com.splitwise.dto.SettlementResponseDTO;
import com.splitwise.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settlements")
public class SettlementController {
    private final SettlementService settlementService;

    @PostMapping
    public SettlementResponseDTO settleExpense(@RequestBody SettlementRequestDTO requestDTO) {
        return settlementService.settleExpense(requestDTO);
    }
}
