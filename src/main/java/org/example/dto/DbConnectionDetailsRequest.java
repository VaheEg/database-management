package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.DbType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConnectionDetailsRequest {

    /**
     * Optional, and only ever your own id: the owner is taken from the token. Sending
     * somebody else's id is rejected rather than silently ignored.
     */
    @Schema(description = "Optional — the owner is always the authenticated caller",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private Long ownerUserId;

    @NotBlank
    @Size(max = 255)
    @Schema(example = "localhost")
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    @Schema(example = "5432")
    private Integer port;

    @NotBlank
    @Size(max = 255)
    @Schema(example = "my_shop")
    private String dbName;

    @NotBlank
    @Size(max = 255)
    @Schema(example = "shop_app")
    private String dbUsername;

    /**
     * Request-only — never returned in a response DTO, and stored encrypted.
     * Required when registering a database; on an update, leaving it out keeps the
     * password already stored.
     */
    @Schema(description = "Required on create; omit on update to keep the stored password",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    private String dbPassword;

    @NotNull
    private DbType dbType;
}
