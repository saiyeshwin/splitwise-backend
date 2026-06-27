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
import com.splitwise.entity.Settlement;
import com.splitwise.repository.ExpensesRepository;
import com.splitwise.repository.ExpenseSplitRepository;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.SettlementRepository;
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
    private final SettlementRepository settlementRepository;

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
        Map<Long, BigDecimal> netBalances = new HashMap<>();
        Map<Long, User> userMap = new HashMap<>();
        for (GroupMember member : members) {
            User u = member.getUser();
            netBalances.put(u.getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            userMap.put(u.getId(), u);
        }

        List<Expense> expenses = expenseRepository.findByGroupId(groupId);
        for (Expense expense : expenses) {
            User payer = expense.getPaidBy();
            Long payerId = payer.getId();
            userMap.put(payerId, payer);
            netBalances.put(payerId, netBalances.getOrDefault(payerId, BigDecimal.ZERO).add(expense.getAmount()));

            List<ExpenseSplit> splits = expenseSplitRepository.findByExpense(expense);
            for (ExpenseSplit split : splits) {
                User splitUser = split.getUser();
                Long splitUserId = splitUser.getId();
                userMap.put(splitUserId, splitUser);
                netBalances.put(splitUserId, netBalances.getOrDefault(splitUserId, BigDecimal.ZERO).subtract(split.getAmount()));
            }
        }

        List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
        for (Settlement settlement : settlements) {
            User fromUser = settlement.getFromUser();
            User toUser = settlement.getToUser();
            Long fromId = fromUser.getId();
            Long toId = toUser.getId();
            userMap.put(fromId, fromUser);
            userMap.put(toId, toUser);
            netBalances.put(fromId, netBalances.getOrDefault(fromId, BigDecimal.ZERO).add(settlement.getAmount()));
            netBalances.put(toId, netBalances.getOrDefault(toId, BigDecimal.ZERO).subtract(settlement.getAmount()));
        }

        List<Long> creditors = new ArrayList<>();
        List<Long> debtors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : netBalances.entrySet()) {
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

        creditors.sort((id1, id2) -> netBalances.get(id2).compareTo(netBalances.get(id1)));
        debtors.sort((id1, id2) -> netBalances.get(id1).compareTo(netBalances.get(id2)));

        while (cIdx < creditors.size() && dIdx < debtors.size()) {
            Long creditorId = creditors.get(cIdx);
            Long debtorId = debtors.get(dIdx);

            BigDecimal creditAmt = netBalances.get(creditorId);
            BigDecimal debtAmt = netBalances.get(debtorId).negate();

            if (creditAmt.compareTo(BigDecimal.ZERO) <= 0) {
                cIdx++;
                continue;
            }
            if (debtAmt.compareTo(BigDecimal.ZERO) <= 0) {
                dIdx++;
                continue;
            }

            BigDecimal settleAmt = creditAmt.min(debtAmt);

            User creditorUser = userMap.get(creditorId);
            User debtorUser = userMap.get(debtorId);

            balances.add(BalanceResponseDTO.builder()
                    .debtorId(debtorId)
                    .debtorName(debtorUser.getName())
                    .creditorId(creditorId)
                    .creditorName(creditorUser.getName())
                    .amount(settleAmt.setScale(2, RoundingMode.HALF_UP))
                    .build());

            netBalances.put(creditorId, creditAmt.subtract(settleAmt));
            netBalances.put(debtorId, netBalances.get(debtorId).add(settleAmt));

            if (creditAmt.subtract(settleAmt).compareTo(BigDecimal.ZERO) <= 0) {
                cIdx++;
            }
            if (netBalances.get(debtorId).compareTo(BigDecimal.ZERO) >= 0) {
                dIdx++;
            }
        }

        return balances;
    }
}