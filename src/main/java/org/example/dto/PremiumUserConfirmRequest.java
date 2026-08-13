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
public class PremiumUserConfirmRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    @Schema(description = "The same address the code was requested for", example = "me@example.com")
    private String connectedEmail;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "must be the six digits from the email")
    @Schema(description = "The six-digit code from the email", example = "123456")
    private String code;
}
