package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnSchemaResponse {

    private String name;
    private String type;
    private boolean nullable;
    private boolean primaryKey;

    /** Set only for a foreign-key column: the table and column it points at. */
    private String referencedTable;
    private String referencedColumn;
}
