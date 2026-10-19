package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class InvitationRequest {

    @Schema(description = "Optional — the sender is the authenticated caller")
    private Long fromUserId;

    @Schema(description = "The recipient by id — or leave it out and send toUsername")
    private Long toUserId;

    @Size(max = 50)
    @Schema(description = "The recipient by username — or leave it out and send toUserId")
    private String toUsername;

    @NotNull
    private Long dbConnectionId;

    @NotNull
    private Long additionalRoleId;
}
