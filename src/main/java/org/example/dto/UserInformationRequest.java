package org.example.dto;

import jakarta.validation.constraints.NotNull;
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

    @NotNull
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
