package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.InvitationStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private Long dbConnectionId;
    private Long additionalRoleId;
    private InvitationStatus status;
    private Instant createdAt;
}
