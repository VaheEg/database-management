package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.AdditionalRoleName;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalRoleResponse {

    private Long id;
    private AdditionalRoleName name;
    private String description;
}
