package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class AuditLogRequest {

    /** Optional: entries are always attributed to the authenticated caller. */
    @Schema(description = "Optional — the entry is attributed to the authenticated caller")
    private Long userId;

    /** Optional, and only a database you own. */
    private Long dbConnectionId;

    @NotBlank
    @Size(max = 255)
    private String action;

    @Size(max = 2000)
    private String detail;

    private boolean success;
}
