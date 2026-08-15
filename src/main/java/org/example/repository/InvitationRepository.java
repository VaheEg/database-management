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

    boolean existsByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
            Long toUserId,
            Long dbConnectionId,
            AdditionalRoleName additionalRoleName,
            InvitationStatus status);

    List<Invitation> findByToUserIdAndDbConnectionIdAndStatus(
            Long toUserId, Long dbConnectionId, InvitationStatus status);

    Optional<Invitation> findByToUserIdAndDbConnectionIdAndAdditionalRoleNameAndStatus(
            Long toUserId,
            Long dbConnectionId,
            AdditionalRoleName additionalRoleName,
            InvitationStatus status);
}
