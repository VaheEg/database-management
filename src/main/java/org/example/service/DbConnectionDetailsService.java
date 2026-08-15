package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.DbConnectionDetailsRequest;
import org.example.dto.DbConnectionDetailsResponse;
import org.example.entity.DbConnectionDetails;
import org.example.entity.InvitationStatus;
import org.example.entity.User;
import org.example.exception.AccessDeniedException;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.InvitationRepository;
import org.example.repository.UserRepository;
import org.example.security.CryptoService;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DbConnectionDetailsService {

    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final CurrentUserService currentUserService;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;

    @Transactional
    public DbConnectionDetailsResponse create(DbConnectionDetailsRequest request) {
        Long ownerId = currentUserService.requireId();
        if (request.getOwnerUserId() != null && !request.getOwnerUserId().equals(ownerId)) {
            throw new AccessDeniedException("You can only register databases under your own account");
        }
        if (request.getDbPassword() == null || request.getDbPassword().isBlank()) {
            throw new BadRequestException("dbPassword is required when registering a database");
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> NotFoundException.of("User", ownerId));

        DbConnectionDetails connection = new DbConnectionDetails();
        connection.setOwnerUser(owner);
        connection.setHost(request.getHost().trim());
        connection.setPort(request.getPort());
        connection.setDbName(request.getDbName().trim());
        connection.setDbUsername(request.getDbUsername().trim());
        connection.setDbPassword(cryptoService.encrypt(request.getDbPassword()));
        connection.setDbType(request.getDbType());

        DbConnectionDetails saved = dbConnectionDetailsRepository.save(connection);
        auditLogService.record(ownerId, saved.getId(), "DB_CONNECTION_CREATED", describe(saved), true);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DbConnectionDetailsResponse get(Long id) {
        return toResponse(findVisibleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<DbConnectionDetailsResponse> list() {
        Long callerId = currentUserService.requireId();

        List<DbConnectionDetails> visible =
                new ArrayList<>(dbConnectionDetailsRepository.findByOwnerUserId(callerId));
        invitationRepository.findByToUserIdAndStatus(callerId, InvitationStatus.ACCEPTED).stream()
                .map(invitation -> invitation.getDbConnection())
                .filter(shared -> visible.stream().noneMatch(own -> own.getId().equals(shared.getId())))
                .forEach(visible::add);

        return visible.stream().map(DbConnectionDetailsService::toResponse).toList();
    }

    @Transactional
    public DbConnectionDetailsResponse update(Long id, DbConnectionDetailsRequest request) {
        DbConnectionDetails connection = findOwnedOrThrow(id);

        connection.setHost(request.getHost().trim());
        connection.setPort(request.getPort());
        connection.setDbName(request.getDbName().trim());
        connection.setDbUsername(request.getDbUsername().trim());
        connection.setDbType(request.getDbType());
        if (request.getDbPassword() != null && !request.getDbPassword().isBlank()) {
            connection.setDbPassword(cryptoService.encrypt(request.getDbPassword()));
        }

        DbConnectionDetails saved = dbConnectionDetailsRepository.save(connection);
        auditLogService.record(currentUserService.requireId(), saved.getId(),
                "DB_CONNECTION_UPDATED", describe(saved), true);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        DbConnectionDetails connection = findOwnedOrThrow(id);
        Long callerId = currentUserService.requireId();
        invitationRepository.deleteAll(invitationRepository.findByDbConnectionId(id));
        auditLogService.detachConnection(id);
        auditLogService.record(callerId, null, "DB_CONNECTION_DELETED", describe(connection), true);
        dbConnectionDetailsRepository.delete(connection);
    }

    @Transactional(readOnly = true)
    public String decryptedPassword(Long id) {
        return cryptoService.decrypt(findOwnedOrThrow(id).getDbPassword());
    }

    private DbConnectionDetails findOwnedOrThrow(Long id) {
        Long callerId = currentUserService.requireId();
        return dbConnectionDetailsRepository.findByIdAndOwnerUserId(id, callerId)
                .orElseThrow(() -> dbConnectionDetailsRepository.existsById(id)
                        ? new AccessDeniedException("Only the owner can change database connection " + id)
                        : NotFoundException.of("Database connection", id));
    }

    private DbConnectionDetails findVisibleOrThrow(Long id) {
        Long callerId = currentUserService.requireId();
        DbConnectionDetails connection = dbConnectionDetailsRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Database connection", id));

        boolean owner = connection.getOwnerUser().getId().equals(callerId);
        boolean sharedWithCaller = !invitationRepository
                .findByToUserIdAndDbConnectionIdAndStatus(callerId, id, InvitationStatus.ACCEPTED)
                .isEmpty();
        if (!owner && !sharedWithCaller) {
            throw new AccessDeniedException(
                    "Database connection " + id + " is neither yours nor shared with you");
        }
        return connection;
    }

    private static String describe(DbConnectionDetails connection) {
        return connection.getDbType() + " " + connection.getDbUsername() + "@"
                + connection.getHost() + ":" + connection.getPort() + "/" + connection.getDbName();
    }

    private static DbConnectionDetailsResponse toResponse(DbConnectionDetails connection) {
        return DbConnectionDetailsResponse.builder()
                .id(connection.getId())
                .ownerUserId(connection.getOwnerUser().getId())
                .host(connection.getHost())
                .port(connection.getPort())
                .dbName(connection.getDbName())
                .dbUsername(connection.getDbUsername())
                .dbType(connection.getDbType())
                .createdAt(connection.getCreatedAt())
                .build();
    }
}
