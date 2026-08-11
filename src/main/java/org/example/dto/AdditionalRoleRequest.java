package org.example.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.AdditionalRoleName;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdditionalRoleRequest {

    @NotNull
    private AdditionalRoleName name;

    @Size(max = 255)
    private String description;
}
