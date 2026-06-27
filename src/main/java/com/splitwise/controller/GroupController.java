package com.splitwise.controller;

import com.splitwise.dto.CreateGroupRequestDTO;
import com.splitwise.dto.GroupResponseDTO;
//import com.splitwise.entity.Group;
import com.splitwise.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.splitwise.dto.BalanceResponseDTO;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;
    @PostMapping
    public GroupResponseDTO createGroup(@Valid @RequestBody CreateGroupRequestDTO requestDTO){
        return groupService.createGroup(requestDTO);
    }
    @GetMapping
    public List<GroupResponseDTO> getGroups(){

        return groupService.getAllGroups();
    }
    @GetMapping("/{groupId}/balances")
    public List<BalanceResponseDTO> getGroupBalances(@PathVariable Long groupId){
        return groupService.getGroupBalances(groupId);
    }



}
