package com.splitwise.service;

import com.splitwise.dto.ExpenseRequestDTO;
import com.splitwise.dto.SplitRequestDTO;
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
    public CreateExpenseResponseDTO createExpense(ExpenseRequestDTO requestDTO){
        User payer = userRepository.findById(requestDTO.getPaidByUserId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
        Group group = groupRepository.findById(requestDTO.getGroupId())
                        .orElseThrow(() -> new RuntimeException("Group not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, payer)) {
            throw new RuntimeException("Payer is not a member of the group");
        }

        Expense expense = new Expense();
        expense.setDescription(requestDTO.getDescription());
        expense.setAmount(requestDTO.getAmount());
        expense.setPaidBy(payer);
        expense.setGroup(group);
        expense.setSplitType(requestDTO.getSplitType());
        Expense savedExpense = expenseRepository.save(expense);

        List<SplitRequestDTO> splits = requestDTO.getSplits();

        if (requestDTO.getSplitType() == SplitType.EQUAL) {
            List<User> splitUsers = new java.util.ArrayList<>();
            if (splits == null || splits.isEmpty()) {
                List<GroupMember> members = groupMemberRepository.findByGroup(group);
                if (members.isEmpty()) {
                    throw new RuntimeException("Group has no members");
                }
                for (GroupMember member : members) {
                    splitUsers.add(member.getUser());
                }
            } else {
                for (SplitRequestDTO split : splits) {
                    User splitUser = userRepository.findById(split.getUserId())
                            .orElseThrow(() -> new RuntimeException("User not found: " + split.getUserId()));
                    if (!groupMemberRepository.existsByGroupAndUser(group, splitUser)) {
                        throw new RuntimeException("Split user is not a member of the group");
                    }
                    splitUsers.add(splitUser);
                }
            }

            BigDecimal splitAmount = requestDTO.getAmount()
                    .divide(BigDecimal.valueOf(splitUsers.size()), 2, RoundingMode.HALF_UP);
            for (User user : splitUsers) {
                ExpenseSplit split = new ExpenseSplit();
                split.setExpense(savedExpense);
                split.setUser(user);
                split.setAmount(splitAmount);
                expenseSplitRepository.save(split);
            }
        } else if (requestDTO.getSplitType() == SplitType.EXACT) {
            if (splits == null || splits.isEmpty()) {
                throw new RuntimeException("Splits must be provided for EXACT split type");
            }
            BigDecimal totalSplitSum = BigDecimal.ZERO;
            for (SplitRequestDTO splitReq : splits) {
                User splitUser = userRepository.findById(splitReq.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found: " + splitReq.getUserId()));
                if (!groupMemberRepository.existsByGroupAndUser(group, splitUser)) {
                    throw new RuntimeException("Split user is not a member of the group");
                }
                totalSplitSum = totalSplitSum.add(splitReq.getValue());
            }

            if (totalSplitSum.compareTo(requestDTO.getAmount()) != 0) {
                throw new RuntimeException("Sum of exact splits must equal the total expense amount");
            }

            for (SplitRequestDTO splitReq : splits) {
                User splitUser = userRepository.getReferenceById(splitReq.getUserId());
                ExpenseSplit split = new ExpenseSplit();
                split.setExpense(savedExpense);
                split.setUser(splitUser);
                split.setAmount(splitReq.getValue());
                expenseSplitRepository.save(split);
            }
        } else if (requestDTO.getSplitType() == SplitType.PERCENT) {
            if (splits == null || splits.isEmpty()) {
                throw new RuntimeException("Splits must be provided for PERCENT split type");
            }
            BigDecimal totalPercentSum = BigDecimal.ZERO;
            for (SplitRequestDTO splitReq : splits) {
                User splitUser = userRepository.findById(splitReq.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found: " + splitReq.getUserId()));
                if (!groupMemberRepository.existsByGroupAndUser(group, splitUser)) {
                    throw new RuntimeException("Split user is not a member of the group");
                }
                totalPercentSum = totalPercentSum.add(splitReq.getValue());
            }

            if (totalPercentSum.compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new RuntimeException("Sum of percentages must equal 100%");
            }

            for (SplitRequestDTO splitReq : splits) {
                User splitUser = userRepository.getReferenceById(splitReq.getUserId());
                BigDecimal splitAmount = requestDTO.getAmount()
                        .multiply(splitReq.getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
                ExpenseSplit split = new ExpenseSplit();
                split.setExpense(savedExpense);
                split.setUser(splitUser);
                split.setAmount(splitAmount);
                expenseSplitRepository.save(split);
            }
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