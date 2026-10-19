package org.example.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    @NotBlank
    @Size(max = 200000)
    @Schema(example = "SELECT id, name FROM customers ORDER BY id")
    private String sql;

    @Min(1)
    @Max(10000)
    @Schema(description = "Optional — return at most this many rows; app.query.max-rows is the ceiling")
    private Integer maxRows;
}
