package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.InvitationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationRequest {

    /** Optional: the sender is always the authenticated caller. */
    @Schema(description = "Optional — the sender is the authenticated caller")
    private Long fromUserId;

    @NotNull
    private Long toUserId;

    @NotNull
    private Long dbConnectionId;

    @NotNull
    private Long additionalRoleId;

    /** Optional on create (defaults to PENDING); used on update to accept/decline/revoke. */
    private InvitationStatus status;
}
