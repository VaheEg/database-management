package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.DbType;

import java.time.Instant;

/** Deliberately omits {@code dbPassword}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbConnectionDetailsResponse {

    private Long id;
    private Long ownerUserId;
    private String host;
    private Integer port;
    private String dbName;
    private String dbUsername;
    private DbType dbType;
    private Instant createdAt;
}
