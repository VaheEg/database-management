package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.AdditionalRoleName;
import org.example.entity.DbType;
import org.example.entity.InvitationStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationResponse {

    private Long id;
    private Long fromUserId;
    private String fromUsername;
    private Long toUserId;
    private String toUsername;
    private Long dbConnectionId;
    private String dbName;
    private DbType dbType;
    private Long additionalRoleId;
    private AdditionalRoleName permission;
    private InvitationStatus status;
    private Instant createdAt;
}
