package org.example.dto;

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

    @NotNull
    private Long ownerUserId;

    @NotBlank
    @Size(max = 255)
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    @NotBlank
    @Size(max = 255)
    private String dbName;

    @NotBlank
    @Size(max = 255)
    private String dbUsername;

    /** Request-only — never returned in a response DTO. */
    @NotBlank
    private String dbPassword;

    @NotNull
    private DbType dbType;
}
