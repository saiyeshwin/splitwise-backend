package com.splitwise.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateGroupRequestDTO {
    @NotBlank
    private String name;
    private Long createdById;
}
