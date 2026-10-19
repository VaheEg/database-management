package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSchemaResponse {

    private String name;

    /** TABLE or VIEW, as the database's own metadata reports it. */
    private String type;

    private List<ColumnSchemaResponse> columns;
}
