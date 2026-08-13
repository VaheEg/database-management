package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Who the presented token resolves to. Returned by {@code GET /api/auth/me}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private Long id;
    private String username;
    private List<String> roles;
}
