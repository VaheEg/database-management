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
public class InvitationStatusRequest {

    @NotNull
    @Schema(description = "ACCEPTED or DECLINED by the recipient, REVOKED by the sender")
    private InvitationStatus status;
}
