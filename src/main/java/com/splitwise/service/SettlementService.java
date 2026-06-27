package com.splitwise.service;

import com.splitwise.dto.SettlementRequestDTO;
import com.splitwise.dto.SettlementResponseDTO;
import com.splitwise.entity.Group;
import com.splitwise.entity.Settlement;
import com.splitwise.entity.User;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementService {
    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public SettlementResponseDTO settleExpense(SettlementRequestDTO requestDTO) {
        Group group = groupRepository.findById(requestDTO.getGroupId())
                .orElseThrow(() -> new RuntimeException("Group not found"));
        User fromUser = userRepository.findById(requestDTO.getFromUserId())
                .orElseThrow(() -> new RuntimeException("Sender User not found"));
        User toUser = userRepository.findById(requestDTO.getToUserId())
                .orElseThrow(() -> new RuntimeException("Recipient User not found"));

        if (!groupMemberRepository.existsByGroupAndUser(group, fromUser)) {
            throw new RuntimeException("Sender is not a member of the group");
        }
        if (!groupMemberRepository.existsByGroupAndUser(group, toUser)) {
            throw new RuntimeException("Recipient is not a member of the group");
        }

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(requestDTO.getAmount())
                .build();

        Settlement saved = settlementRepository.save(settlement);

        return SettlementResponseDTO.builder()
                .id(saved.getId())
                .groupId(group.getId())
                .fromUserId(fromUser.getId())
                .fromUserName(fromUser.getName())
                .toUserId(toUser.getId())
                .toUserName(toUser.getName())
                .amount(saved.getAmount())
                .build();
    }
}
