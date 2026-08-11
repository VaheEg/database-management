package org.example.dto;

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

    @NotNull
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
