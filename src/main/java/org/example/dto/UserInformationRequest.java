package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInformationRequest {

    /** Optional: the profile always belongs to the authenticated caller. */
    @Schema(description = "Optional — the profile belongs to the authenticated caller")
    private Long userId;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 30)
    private String phoneNumber;

    private LocalDate dateOfBirth;

    @Size(max = 100)
    private String country;

    @Size(max = 100)
    private String city;

    @Size(max = 512)
    private String avatarUrl;
}
