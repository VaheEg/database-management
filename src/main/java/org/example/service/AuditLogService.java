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

/**
 * The audit trail: who did what, against which database, whether it worked, and when.
 *
 * <p>Entries are append-only. There is deliberately no update or delete — a log you
 * can edit is not evidence of anything — so the controller exposes only create and
 * read. Other services call {@link #record} rather than the DTO-facing
 * {@link #create}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final CurrentUserService currentUserService;

    /**
     * Writes an entry from inside another operation.
     *
     * <p>Joins the caller's transaction rather than opening its own. That is deliberate:
     * an entry usually points at the very row the caller just inserted, and a separate
     * transaction could not see that uncommitted row — the foreign key check would block
     * on it while the caller waits for the audit write, which is a deadlock. The cost is
     * that an entry rolls back with the operation it describes, so this records actions
     * that succeeded. Recording a failed attempt means calling it from a fresh
     * transaction after the rollback, with {@code dbConnectionId} pointing only at rows
     * that are already committed.
     *
     * @param dbConnectionId the database involved, or {@code null} for account-level actions
     */
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

    /**
     * Unlinks past entries from a database that is about to be un-registered, so the
     * history survives the deletion instead of being cascaded away with it. The entries
     * keep their action and detail — including which host and database they were about.
     */
    @Transactional
    public void detachConnection(Long dbConnectionId) {
        List<AuditLog> entries = auditLogRepository.findByDbConnectionId(dbConnectionId);
        entries.forEach(entry -> entry.setDbConnection(null));
        auditLogRepository.saveAll(entries);
        log.debug("Detached {} audit entr(ies) from connection {}", entries.size(), dbConnectionId);
    }

    /** Records an action the caller performed by hand through the API. */
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

    /** Everything the caller did, newest first. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> list() {
        return auditLogRepository.findByUserIdOrderByPerformedAtDesc(currentUserService.requireId())
                .stream()
                .map(AuditLogService::toResponse)
                .toList();
    }

    /** The trail for one database. Owners only — it shows what collaborators did. */
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

    /** The column holds 2000 characters; a long statement should not fail the write. */
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
