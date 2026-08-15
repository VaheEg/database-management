package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AuditLogRequest;
import org.example.dto.AuditLogResponse;
import org.example.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "6. Audit log", description = "Append-only record of who did what, and when")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    @Operation(summary = "Record an entry",
            description = "Always attributed to the caller. Most entries are written by the API itself.")
    public ResponseEntity<AuditLogResponse> create(@Valid @RequestBody AuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one entry",
            description = "Yours, or one against a database you own.")
    public ResponseEntity<AuditLogResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(auditLogService.get(id));
    }

    @GetMapping
    @Operation(summary = "Everything you did, newest first")
    public ResponseEntity<List<AuditLogResponse>> list() {
        return ResponseEntity.ok(auditLogService.list());
    }

    @GetMapping("/connection/{dbConnectionId}")
    @Operation(summary = "The trail for one database",
            description = "Owner only — it shows what collaborators did with the access they were granted.")
    public ResponseEntity<List<AuditLogResponse>> listForConnection(
            @PathVariable("dbConnectionId") Long dbConnectionId) {
        return ResponseEntity.ok(auditLogService.listForConnection(dbConnectionId));
    }
}
