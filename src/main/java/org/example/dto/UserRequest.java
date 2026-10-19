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
public class UserRequest {

    @NotBlank
    @Size(max = 50)
    private String username;

    @Email
    @Size(max = 255)
    @Schema(description = "Optional — leave it out to have no sign-in email")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
