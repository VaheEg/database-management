package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuditLogRequest;
import org.example.dto.AuditLogResponse;
import org.example.entity.AuditLog;
import org.example.entity.DbConnectionDetails;
import org.example.entity.User;
import org.example.exception.AccessDeniedException;
import org.example.exception.NotFoundException;
import org.example.repository.AuditLogRepository;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public void record(Long userId, Long dbConnectionId, String action, String detail, boolean success) {
        AuditLog entry = new AuditLog();
        entry.setUser(userRepository.getReferenceById(userId));
        if (dbConnectionId != null) {
            entry.setDbConnection(dbConnectionDetailsRepository.getReferenceById(dbConnectionId));
        }
        entry.setAction(action);
        entry.setDetail(truncate(detail));
        entry.setSuccess(success);
        auditLogRepository.save(entry);
    }

    @Transactional
    public void detachConnection(Long dbConnectionId) {
        List<AuditLog> entries = auditLogRepository.findByDbConnectionId(dbConnectionId);
        entries.forEach(entry -> entry.setDbConnection(null));
        auditLogRepository.saveAll(entries);
        log.debug("Detached {} audit entr(ies) from connection {}", entries.size(), dbConnectionId);
    }

    @Transactional
    public AuditLogResponse create(AuditLogRequest request) {
        Long userId = currentUserService.requireId();
        if (request.getUserId() != null && !request.getUserId().equals(userId)) {
            currentUserService.requireSelf(request.getUserId(), "record audit entries for yourself");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        AuditLog entry = new AuditLog();
        entry.setUser(user);
        if (request.getDbConnectionId() != null) {
            entry.setDbConnection(visibleConnectionOrThrow(request.getDbConnectionId(), userId));
        }
        entry.setAction(request.getAction());
        entry.setDetail(truncate(request.getDetail()));
        entry.setSuccess(request.isSuccess());
        return toResponse(auditLogRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(Long id) {
        AuditLog entry = auditLogRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Audit entry", id));
        Long callerId = currentUserService.requireId();

        boolean ownEntry = entry.getUser().getId().equals(callerId);
        boolean ownsTheDatabase = entry.getDbConnection() != null
                && dbConnectionDetailsRepository
                .findByIdAndOwnerUserId(entry.getDbConnection().getId(), callerId).isPresent();
        if (!ownEntry && !ownsTheDatabase) {
            throw new AccessDeniedException("This audit entry belongs to another user");
        }
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list() {
        return auditLogRepository.findByUserIdOrderByPerformedAtDesc(currentUserService.requireId())
                .stream()
                .map(AuditLogService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listForConnection(Long dbConnectionId) {
        Long callerId = currentUserService.requireId();
        dbConnectionDetailsRepository.findByIdAndOwnerUserId(dbConnectionId, callerId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Only the owner of a database can read its audit trail"));
        return auditLogRepository.findByDbConnectionIdOrderByPerformedAtDesc(dbConnectionId).stream()
                .map(AuditLogService::toResponse)
                .toList();
    }

    private DbConnectionDetails visibleConnectionOrThrow(Long dbConnectionId, Long callerId) {
        return dbConnectionDetailsRepository.findByIdAndOwnerUserId(dbConnectionId, callerId)
                .orElseThrow(() -> new AccessDeniedException(
                        "Database connection " + dbConnectionId + " is not yours"));
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= 2000) {
            return detail;
        }
        return detail.substring(0, 1997) + "...";
    }

    private static AuditLogResponse toResponse(AuditLog entry) {
        return AuditLogResponse.builder()
                .id(entry.getId())
                .userId(entry.getUser().getId())
                .dbConnectionId(entry.getDbConnection() == null ? null : entry.getDbConnection().getId())
                .action(entry.getAction())
                .detail(entry.getDetail())
                .success(entry.isSuccess())
                .performedAt(entry.getPerformedAt())
                .build();
    }
}
