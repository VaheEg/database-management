package org.example.repository;

import org.example.entity.PremiumUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PremiumUserRepository extends JpaRepository<PremiumUser, Long> {

    Optional<PremiumUser> findByUserId(Long userId);

    Optional<PremiumUser> findByConnectedEmail(String connectedEmail);

    boolean existsByUserId(Long userId);
}
