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

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
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

}