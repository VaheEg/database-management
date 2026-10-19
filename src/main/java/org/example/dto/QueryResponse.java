package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.AdditionalRoleName;

import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    /** The statement's verb: SELECT, INSERT, UPDATE, DELETE, or e.g. CREATE for an owner-only statement. */
    private String statementType;

    /** The shared permissions the statement needed; empty for an owner-only statement. */
    private Set<AdditionalRoleName> permissionsUsed;

    /** Column labels, empty when the statement returned no result set. */
    private List<String> columns;

    private List<List<Object>> rows;

    /** Rows affected by an INSERT, UPDATE or DELETE without RETURNING; null for a result set. */
    private Integer updateCount;

    /** True when more rows existed than were returned. */
    private boolean truncated;

    private long durationMs;
}
