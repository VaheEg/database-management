package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.InvitationRequest;
import org.example.dto.InvitationResponse;
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
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sharing: an owner invites another user to one of their databases with exactly one
 * database-scoped permission.
 *
 * <p>Who may do what is the whole point of this class:
 * <ul>
 *   <li>only the <b>owner of that database</b> may send an invitation for it;</li>
 *   <li>only the <b>recipient</b> may accept or decline it;</li>
 *   <li>only the <b>sender</b> may revoke or delete it.</li>
 * </ul>
 *
 * <p>A grant is tied to one {@code dbConnectionId}, so UPDATE_ROLE on database A says
 * nothing about the same owner's database B.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final AdditionalRoleRepository additionalRoleRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    /** Sends an invitation. The sender is the caller, who must own the database. */
    @Transactional
    public InvitationResponse create(InvitationRequest request) {
        Long senderId = currentUserService.requireId();
        if (request.getFromUserId() != null && !request.getFromUserId().equals(senderId)) {
            throw new AccessDeniedException("You can only send invitations from your own account");
        }
        if (request.getToUserId().equals(senderId)) {
            throw new BadRequestException("You already own this database — there is nothing to grant yourself");
        }

        DbConnectionDetails connection = dbConnectionDetailsRepository
                .findByIdAndOwnerUserId(request.getDbConnectionId(), senderId)
                .orElseThrow(() -> dbConnectionDetailsRepository.existsById(request.getDbConnectionId())
                        ? new AccessDeniedException("Only the owner of a database can share it")
                        : NotFoundException.of("Database connection", request.getDbConnectionId()));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> NotFoundException.of("User", senderId));
        User recipient = userRepository.findById(request.getToUserId())
                .orElseThrow(() -> NotFoundException.of("User", request.getToUserId()));
        AdditionalRole permission = additionalRoleRepository.findById(request.getAdditionalRoleId())
                .orElseThrow(() -> NotFoundException.of("Permission", request.getAdditionalRoleId()));

        rejectDuplicate(recipient.getId(), connection.getId(), permission);

        Invitation invitation = new Invitation();
        invitation.setFromUser(sender);
        invitation.setToUser(recipient);
        invitation.setDbConnection(connection);
        invitation.setAdditionalRole(permission);
        // Always starts pending: a status in the request would let a sender grant
        // themselves an accepted invitation on somebody else's behalf.
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

    /** Everything the caller sent or received, newest first. */
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

    /**
     * Responds to an invitation. The only meaningful field in the request is
     * {@code status}: ACCEPTED or DECLINED from the recipient, REVOKED from the sender.
     */
    @Transactional
    public InvitationResponse update(Long id, InvitationRequest request) {
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Invitation", id));
        Long callerId = currentUserService.requireId();
        InvitationStatus target = request.getStatus();

        if (target == null) {
            throw new BadRequestException(
                    "Set 'status' to ACCEPTED, DECLINED (recipient) or REVOKED (sender)");
        }
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

    /** Withdraws an invitation entirely. Sender only; revoking is usually the better record. */
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

    /**
     * The authorization question the rest of the application asks: may this user do
     * this to this one database? The owner may do anything to their own; everyone else
     * needs an accepted invitation carrying that permission <em>for that database</em>.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long dbConnectionId, AdditionalRoleName permission) {
        if (dbConnectionDetailsRepository.findByIdAndOwnerUserId(dbConnectionId, userId).isPresent()) {
            return true;
        }
        return invitationRepository.existsByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
                userId, dbConnectionId, permission, InvitationStatus.ACCEPTED);
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
                .toUserId(invitation.getToUser().getId())
                .dbConnectionId(invitation.getDbConnection().getId())
                .additionalRoleId(invitation.getAdditionalRole().getId())
                .status(invitation.getStatus())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}
