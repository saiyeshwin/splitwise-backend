package com.splitwise.service;

import com.splitwise.dto.AddMemberRequestDTO;
import com.splitwise.dto.GroupMemberResponseDTO;
import com.splitwise.entity.Group;
import com.splitwise.entity.GroupMember;
import com.splitwise.entity.User;
import com.splitwise.repository.GroupMemberRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class GroupMemberService {
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    public GroupMemberResponseDTO addMember(Long groupId, AddMemberRequestDTO requestDTO){
        Group group = groupRepository.findById(groupId)
                        .orElseThrow(() -> new RuntimeException("Group not found"));
        User user = userRepository.findById(requestDTO.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found"));
        if(groupMemberRepository.existsByGroupAndUser(group, user)){
            throw new RuntimeException("User already exists in group");
        }
        GroupMember groupMember = new GroupMember();
        groupMember.setGroup(group);
        groupMember.setUser(user);
        GroupMember savedMember = groupMemberRepository.save(groupMember);
        GroupMemberResponseDTO response = new GroupMemberResponseDTO();
        response.setGroupId(savedMember.getGroup().getId());
        response.setGroupName(savedMember.getGroup().getName());
        response.setUserId(savedMember.getUser().getId());
        response.setUserName(savedMember.getUser().getName());
        return response;
    }

    public List<GroupMemberResponseDTO> getMembers(Long groupId) {
        Group group = groupRepository.findById(groupId)
                        .orElseThrow(() -> new RuntimeException("Group not found"));
        return groupMemberRepository.findByGroup(group).stream()
                .map(gm -> {
                    GroupMemberResponseDTO dto = new GroupMemberResponseDTO();
                    dto.setGroupId(gm.getGroup().getId());
                    dto.setGroupName(gm.getGroup().getName());
                    dto.setUserId(gm.getUser().getId());
                    dto.setUserName(gm.getUser().getName());
                    dto.setUserEmail(gm.getUser().getEmail());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
