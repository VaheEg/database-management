package org.example.repository;

import org.example.entity.DbConnectionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DbConnectionDetailsRepository extends JpaRepository<DbConnectionDetails, Long> {

    List<DbConnectionDetails> findByOwnerUserId(Long ownerUserId);

    Optional<DbConnectionDetails> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
