package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "may contain only letters, digits, dots, underscores and hyphens")
    private String username;

    @Email
    @Size(max = 255)
    @Schema(description = "Optional — lets you sign in with it instead of the username. "
            + "The premium upgrade verifies its own address, so an account needs no email to start")
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;
}
