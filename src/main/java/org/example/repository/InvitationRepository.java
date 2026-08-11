package org.example.repository;

import org.example.entity.AdditionalRoleName;
import org.example.entity.Invitation;
import org.example.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findByFromUserId(Long fromUserId);

    List<Invitation> findByToUserId(Long toUserId);

    List<Invitation> findByDbConnectionId(Long dbConnectionId);

    List<Invitation> findByToUserIdAndStatus(Long toUserId, InvitationStatus status);

    List<Invitation> findByFromUserIdAndStatus(Long fromUserId, InvitationStatus status);

    /**
     * The authorization lookup: does this user hold this permission on this one
     * database? Scoped by {@code dbConnectionId}, so a grant on database A can
     * never satisfy a check against the owner's database B.
     */
    boolean existsByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
            Long toUserId,
            Long dbConnectionId,
            AdditionalRoleName additionalRoleName,
            InvitationStatus status);

    /** Every permission a collaborator currently holds on one database. */
    List<Invitation> findByToUserIdAndDbConnectionIdAndStatus(
            Long toUserId, Long dbConnectionId, InvitationStatus status);

    /** Used to reject duplicate invitations for the same db + permission pair. */
    Optional<Invitation> findByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
            Long toUserId,
            Long dbConnectionId,
            AdditionalRoleName additionalRoleName,
            InvitationStatus status);
}
