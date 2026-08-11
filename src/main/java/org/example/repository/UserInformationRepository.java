package org.example.repository;

import org.example.entity.UserInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserInformationRepository extends JpaRepository<UserInformation, Long> {

    Optional<UserInformation> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
