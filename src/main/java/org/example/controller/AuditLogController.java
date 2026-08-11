package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AuditLogRequest;
import org.example.dto.AuditLogResponse;
import org.example.service.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @PostMapping
    public ResponseEntity<AuditLogResponse> create(@Valid @RequestBody AuditLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auditLogService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(auditLogService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> list() {
        return ResponseEntity.ok(auditLogService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuditLogResponse> update(@PathVariable("id") Long id,
                                                   @Valid @RequestBody AuditLogRequest request) {
        return ResponseEntity.ok(auditLogService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        auditLogService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
