package org.example.repository;

import org.example.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findTopByUserIdOrderByIdDesc(Long userId);

    void deleteByUserId(Long userId);
}
