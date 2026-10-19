package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.InvitationRequest;
import org.example.dto.InvitationResponse;
import org.example.dto.InvitationStatusRequest;
import org.example.entity.AdditionalRole;
import org.example.entity.AdditionalRoleName;
import org.example.entity.DbConnectionDetails;
import org.example.entity.Invitation;
import org.example.entity.InvitationStatus;
import org.example.entity.User;
import org.example.exception.AccessDeniedException;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.AdditionalRoleRepository;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.InvitationRepository;
import org.example.repository.PremiumUserRepository;
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final AdditionalRoleRepository additionalRoleRepository;
    private final PremiumUserRepository premiumUserRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    @Transactional
    public InvitationResponse create(InvitationRequest request) {
        Long senderId = currentUserService.requireId();
        if (request.getFromUserId() != null && !request.getFromUserId().equals(senderId)) {
            throw new AccessDeniedException("You can only send invitations from your own account");
        }
        if (!premiumUserRepository.existsByUserId(senderId)) {
            throw new AccessDeniedException(
                    "Sharing a database is a premium feature — connect and verify an email "
                            + "address via /api/premium-users/request-code first");
        }
        DbConnectionDetails connection = dbConnectionDetailsRepository
                .findByIdAndOwnerUserId(request.getDbConnectionId(), senderId)
                .orElseThrow(() -> dbConnectionDetailsRepository.existsById(request.getDbConnectionId())
                        ? new AccessDeniedException("Only the owner of a database can share it")
                        : NotFoundException.of("Database connection", request.getDbConnectionId()));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> NotFoundException.of("User", senderId));
        User recipient = findRecipient(request);
        if (recipient.getId().equals(senderId)) {
            throw new BadRequestException("You already own this database — there is nothing to grant yourself");
        }
        AdditionalRole permission = additionalRoleRepository.findById(request.getAdditionalRoleId())
                .orElseThrow(() -> NotFoundException.of("Permission", request.getAdditionalRoleId()));

        rejectDuplicate(recipient.getId(), connection.getId(), permission);

        Invitation invitation = new Invitation();
        invitation.setFromUser(sender);
        invitation.setToUser(recipient);
        invitation.setDbConnection(connection);
        invitation.setAdditionalRole(permission);
        invitation.setStatus(InvitationStatus.PENDING);

        Invitation saved = invitationRepository.save(invitation);
        auditLogService.record(senderId, connection.getId(), "INVITATION_SENT",
                permission.getName() + " offered to user " + recipient.getId(), true);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvitationResponse get(Long id) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Invitation", id));
        Long callerId = currentUserService.requireId();
        if (!isParty(invitation, callerId)) {
            throw new AccessDeniedException("This invitation does not involve you");
        }
        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> list() {
        Long callerId = currentUserService.requireId();
        List<Invitation> mine = new ArrayList<>(invitationRepository.findByFromUserId(callerId));
        invitationRepository.findByToUserId(callerId).stream()
                .filter(received -> mine.stream().noneMatch(sent -> sent.getId().equals(received.getId())))
                .forEach(mine::add);

        return mine.stream()
                .sorted(Comparator.comparing(Invitation::getCreatedAt).reversed())
                .map(InvitationService::toResponse)
                .toList();
    }

    @Transactional
    public InvitationResponse update(Long id, InvitationStatusRequest request) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Invitation", id));
        Long callerId = currentUserService.requireId();
        InvitationStatus target = request.getStatus();

        if (target == InvitationStatus.PENDING) {
            throw new BadRequestException("An invitation cannot be moved back to PENDING");
        }

        switch (target) {
            case ACCEPTED, DECLINED -> {
                if (!invitation.getToUser().getId().equals(callerId)) {
                    throw new AccessDeniedException("Only the recipient can accept or decline an invitation");
                }
                if (invitation.getStatus() != InvitationStatus.PENDING) {
                    throw new BadRequestException(
                            "This invitation is already " + invitation.getStatus() + " and cannot be answered again");
                }
            }
            case REVOKED -> {
                if (!invitation.getFromUser().getId().equals(callerId)) {
                    throw new AccessDeniedException("Only the sender can revoke an invitation");
                }
                if (invitation.getStatus() == InvitationStatus.REVOKED) {
                    throw new BadRequestException("This invitation is already revoked");
                }
            }
            default -> throw new BadRequestException("Unsupported status " + target);
        }

        invitation.setStatus(target);
        Invitation saved = invitationRepository.save(invitation);
        auditLogService.record(callerId, invitation.getDbConnection().getId(), "INVITATION_" + target,
                invitation.getAdditionalRole().getName() + " on connection "
                        + invitation.getDbConnection().getId(), true);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Invitation", id));
        Long callerId = currentUserService.requireId();
        if (!invitation.getFromUser().getId().equals(callerId)) {
            throw new AccessDeniedException("Only the sender can delete an invitation");
        }
        auditLogService.record(callerId, invitation.getDbConnection().getId(), "INVITATION_DELETED",
                invitation.getAdditionalRole().getName() + " withdrawn from user "
                        + invitation.getToUser().getId(), true);
        invitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long dbConnectionId, AdditionalRoleName permission) {
        if (dbConnectionDetailsRepository.findByIdAndOwnerUserId(dbConnectionId, userId).isPresent()) {
            return true;
        }
        return invitationRepository.existsByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
                userId, dbConnectionId, permission, InvitationStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public void requirePermission(Long userId, Long dbConnectionId, AdditionalRoleName permission) {
        if (!hasPermission(userId, dbConnectionId, permission)) {
            throw new AccessDeniedException("You need " + permission + " on database connection "
                    + dbConnectionId + " — ask its owner for an invitation");
        }
    }

    /** The recipient named by username or by id. Either will do; both must agree when both are sent. */
    private User findRecipient(InvitationRequest request) {
        String username = request.getToUsername() == null ? "" : request.getToUsername().trim();
        if (username.isEmpty() && request.getToUserId() == null) {
            throw new BadRequestException("Name the recipient with toUsername or toUserId");
        }
        User recipient = username.isEmpty()
                ? userRepository.findById(request.getToUserId())
                        .orElseThrow(() -> NotFoundException.of("User", request.getToUserId()))
                : userRepository.findByUsername(username)
                        .orElseThrow(() -> new NotFoundException("There is no user called '" + username + "'"));
        if (request.getToUserId() != null && !request.getToUserId().equals(recipient.getId())) {
            throw new BadRequestException("toUsername and toUserId name two different users");
        }
        return recipient;
    }

    private void rejectDuplicate(Long recipientId, Long connectionId, AdditionalRole permission) {
        for (InvitationStatus status : List.of(InvitationStatus.PENDING, InvitationStatus.ACCEPTED)) {
            invitationRepository.findByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
                    recipientId, connectionId, permission.getName(), status).ifPresent(existing -> {
                throw new BadRequestException("That user already has a " + status
                        + " invitation for " + permission.getName() + " on this database");
            });
        }
    }

    private static boolean isParty(Invitation invitation, Long userId) {
        return invitation.getFromUser().getId().equals(userId)
                || invitation.getToUser().getId().equals(userId);
    }

    private static InvitationResponse toResponse(Invitation invitation) {
        return InvitationResponse.builder()
                .id(invitation.getId())
                .fromUserId(invitation.getFromUser().getId())
                .fromUsername(invitation.getFromUser().getUsername())
                .toUserId(invitation.getToUser().getId())
                .toUsername(invitation.getToUser().getUsername())
                .dbConnectionId(invitation.getDbConnection().getId())
                .dbName(invitation.getDbConnection().getDbName())
                .dbType(invitation.getDbConnection().getDbType())
                .additionalRoleId(invitation.getAdditionalRole().getId())
                .permission(invitation.getAdditionalRole().getName())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
