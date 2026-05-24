package com.splitwise.dto;

import lombok.Data;

@Data
public class GroupResponseDTO {
    private Long id;
    private String name;
    private Long createdById;
    private String createdByName;

}