package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * What a successful register or login returns. Send the token back as
 * {@code Authorization: Bearer <token>} on every subsequent call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private Instant expiresAt;
    private Long userId;
    private String username;
    private List<String> roles;
}
