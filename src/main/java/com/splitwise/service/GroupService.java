package com.splitwise.service;

import com.splitwise.dto.CreateGroupRequestDTO;
import com.splitwise.dto.GroupResponseDTO;
import com.splitwise.entity.Group;
import com.splitwise.entity.User;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import com.splitwise.dto.BalanceResponseDTO;
import com.splitwise.entity.Expense;
import com.splitwise.entity.ExpenseSplit;
import com.splitwise.entity.GroupMember;
import com.splitwise.repository.ExpensesRepository;
import com.splitwise.repository.ExpenseSplitRepository;
import com.splitwise.repository.GroupMemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpensesRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;

    public GroupResponseDTO createGroup(CreateGroupRequestDTO requestDTO){
        User creator = userRepository.findById(requestDTO.getCreatedById())
                        .orElseThrow(() -> new RuntimeException("User not found"));
        Group group = new Group();
        group.setName(requestDTO.getName());
        group.setCreatedBy(creator);
        groupRepository.save(group);
        GroupResponseDTO groupResponseDTO=new GroupResponseDTO();
//        groupResponseDTO.setCreatedById(group.getCreatedBy().getId());
        groupResponseDTO.setId(group.getId());
        groupResponseDTO.setName(group.getName());
        groupResponseDTO.setCreatedById(creator.getId());
        groupResponseDTO.setCreatedByName(creator.getName());
        return groupResponseDTO;
    }

    public List<GroupResponseDTO> getAllGroups(){
        List<Group> groups = groupRepository.findAll();
        return groups.stream()
                .map(group -> {
                    GroupResponseDTO response = new GroupResponseDTO();
                    response.setId(group.getId());
                    response.setName(group.getName());
                    response.setCreatedById(group.getCreatedBy().getId());
                    response.setCreatedByName(group.getCreatedBy().getName());
                    return response;
                })
                .toList();
    }

    public List<BalanceResponseDTO> getGroupBalances(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        
        List<GroupMember> members = groupMemberRepository.findByGroup(group);
        Map<User, BigDecimal> netBalances = new HashMap<>();
        for (GroupMember member : members) {
            netBalances.put(member.getUser(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        for (Expense expense : expenses) {
            User payer = expense.getPaidBy();
            netBalances.put(payer, netBalances.getOrDefault(payer, BigDecimal.ZERO).add(expense.getAmount()));

            List<ExpenseSplit> splits = expenseSplitRepository.findByExpense(expense);
            for (ExpenseSplit split : splits) {
                User splitUser = split.getUser();
                netBalances.put(splitUser, netBalances.getOrDefault(splitUser, BigDecimal.ZERO).subtract(split.getAmount()));
            }
        }

        List<User> creditors = new ArrayList<>();
        List<User> debtors = new ArrayList<>();

        for (Map.Entry<User, BigDecimal> entry : netBalances.entrySet()) {
            BigDecimal bal = entry.getValue();
            if (bal.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(entry.getKey());
            } else if (bal.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(entry.getKey());
            }
        }

        List<BalanceResponseDTO> balances = new ArrayList<>();
        int cIdx = 0;
        int dIdx = 0;

        creditors.sort((u1, u2) -> netBalances.get(u2).compareTo(netBalances.get(u1)));
        debtors.sort((u1, u2) -> netBalances.get(u1).compareTo(netBalances.get(u2)));

        while (cIdx < creditors.size() && dIdx < debtors.size()) {
            User creditor = creditors.get(cIdx);
            User debtor = debtors.get(dIdx);

            BigDecimal creditAmt = netBalances.get(creditor);
            BigDecimal debtAmt = netBalances.get(debtor).negate();

            if (creditAmt.compareTo(BigDecimal.ZERO) <= 0) {
                cIdx++;
                continue;
            }
            if (debtAmt.compareTo(BigDecimal.ZERO) <= 0) {
                dIdx++;
                continue;
            }

            BigDecimal settleAmt = creditAmt.min(debtAmt);

            balances.add(BalanceResponseDTO.builder()
                    .debtorId(debtor.getId())
                    .debtorName(debtor.getName())
                    .creditorId(creditor.getId())
                    .creditorName(creditor.getName())
                    .amount(settleAmt.setScale(2, RoundingMode.HALF_UP))
                    .build());

            netBalances.put(creditor, creditAmt.subtract(settleAmt));
            netBalances.put(debtor, netBalances.get(debtor).add(settleAmt));

            if (creditAmt.subtract(settleAmt).compareTo(BigDecimal.ZERO) <= 0) {
                cIdx++;
            }
            if (netBalances.get(debtor).compareTo(BigDecimal.ZERO) >= 0) {
                dIdx++;
            }
        }

        return balances;
    }
}