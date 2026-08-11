package org.example.repository;

import org.example.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /**
     * Loads a user together with their application roles. Used at authentication
     * time, where {@code spring.jpa.open-in-view=false} means lazy roles would
     * otherwise fail once the transaction closes.
     */
    @Query("""
            select distinct u from User u
            left join fetch u.userRoles ur
            left join fetch ur.role
            where u.username = :username
            """)
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
