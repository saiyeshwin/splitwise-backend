package com.splitwise.service;

import com.splitwise.dto.CreateExpenseRequestDTO;
import com.splitwise.dto.CreateExpenseResponseDTO;
import com.splitwise.entity.*;
import com.splitwise.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.List;
@Service
@RequiredArgsConstructor
public class ExpenseService {
    private final ExpensesRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    @Transactional
    public CreateExpenseResponseDTO createExpense(CreateExpenseRequestDTO requestDTO){
        User payer = userRepository.findById(requestDTO.getPaidById())
                        .orElseThrow(() -> new RuntimeException("User not found"));
        Group group = groupRepository.findById(requestDTO.getGroupId())
                        .orElseThrow(() -> new RuntimeException("Group not found"));
        Expense expense = new Expense();
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaidBy(payer);
        expense.setGroup(group);
        expense.setSplitType(SplitType.EQUAL);
        Expense savedExpense = expenseRepository.save(expense);
        List<GroupMember> members = groupMemberRepository.findByGroup(group);
        if(members.isEmpty()){
            throw new RuntimeException("Group has no members");
        }
        BigDecimal splitAmount = requestDTO.getAmount()
                        .divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.HALF_UP);
        for(GroupMember member : members){
            ExpenseSplit split = new ExpenseSplit();
            split.setExpense(savedExpense);
            split.setUser(member.getUser());
            split.setAmount(splitAmount);
            expenseSplitRepository.save(split);
        }
        CreateExpenseResponseDTO response = new CreateExpenseResponseDTO();
        response.setExpenseId(savedExpense.getId());
        response.setDescription(savedExpense.getDescription());
        response.setAmount(savedExpense.getAmount());
        response.setPaidBy(payer.getName());
        response.setGroupName(group.getName());
        return response;
    }
}