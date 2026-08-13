package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumUserRequest {

    @Schema(description = "Optional — the upgrade applies to the authenticated caller")
    private Long userId;

    @NotBlank
    @Email
    @Size(max = 255)
    private String connectedEmail;
}
