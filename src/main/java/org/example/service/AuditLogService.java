package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.AuditLogRequest;
import org.example.dto.AuditLogResponse;
import org.example.repository.AuditLogRepository;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;

    public AuditLogResponse create(AuditLogRequest request) {
        // TODO: resolve the User and optional DbConnectionDetails, persist the entry
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AuditLogResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<AuditLogResponse> list() {
        // TODO: load all entries and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AuditLogResponse update(Long id, AuditLogRequest request) {
        // TODO: audit entries are normally append-only — decide whether to keep this
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
