package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private Long userId;

    private Long dbConnectionId;

    @NotBlank
    @Size(max = 255)
    private String action;

    @Size(max = 2000)
    private String detail;

    private boolean success;
}
