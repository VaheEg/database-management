package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumUserResponse {

    private Long id;
    private Long userId;
    private String connectedEmail;
    private Instant activatedAt;
}
