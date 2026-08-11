package org.example.repository;

import org.example.entity.AdditionalRole;
import org.example.entity.AdditionalRoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdditionalRoleRepository extends JpaRepository<AdditionalRole, Long> {

    Optional<AdditionalRole> findByName(AdditionalRoleName name);

    boolean existsByName(AdditionalRoleName name);
}
