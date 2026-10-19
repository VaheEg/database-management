package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.QueryRequest;
import org.example.dto.QueryResponse;
import org.example.dto.ScriptResponse;
import org.example.dto.TableSchemaResponse;
import org.example.service.QueryExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/db-connections/{id}")
@RequiredArgsConstructor
@Tag(name = "4. Databases")
public class QueryController {

    private final QueryExecutionService queryExecutionService;

    @PostMapping("/query")
    @Operation(summary = "Run one SQL statement against a database you own or that is shared with you",
            description = """
                    One statement per call. The verb decides the permission needed (SELECT → \
                    SELECT_ROLE and so on; an upsert needs INSERT and UPDATE); anything else — CREATE \
                    TABLE, ALTER, DROP — only the owner may run. SELECTs run read-only. Every attempt \
                    is audited. A database that cannot be reached answers 502.""")
    public ResponseEntity<QueryResponse> query(@PathVariable("id") Long id,
                                               @Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryExecutionService.execute(id, request));
    }

    @PostMapping("/script")
    @Operation(summary = "Run several ';'-separated statements in one transaction",
            description = """
                    Each statement is checked exactly like /query before the first one runs; if any \
                    fails, everything is rolled back (MySQL commits DDL implicitly, so CREATE/ALTER \
                    there cannot be undone). One result per statement, in order.""")
    public ResponseEntity<ScriptResponse> script(@PathVariable("id") Long id,
                                                 @Valid @RequestBody QueryRequest request) {
        return ResponseEntity.ok(queryExecutionService.executeScript(id, request));
    }

    @GetMapping("/schema")
    @Operation(summary = "Tables and columns of a database, with primary and foreign keys",
            description = "Read live from the database's own metadata. Needs SELECT_ROLE.")
    public ResponseEntity<List<TableSchemaResponse>> schema(@PathVariable("id") Long id) {
        return ResponseEntity.ok(queryExecutionService.schema(id));
    }
}
