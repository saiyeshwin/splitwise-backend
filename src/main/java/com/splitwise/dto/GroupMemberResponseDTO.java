package com.splitwise.dto;
import lombok.Data;
@Data
public class GroupMemberResponseDTO {
    private Long groupId;
    private String groupName;
    private Long userId;
    private String userName;
    private String userEmail;
}