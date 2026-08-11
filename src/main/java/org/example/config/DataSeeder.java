package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.AdditionalRole;
import org.example.entity.AdditionalRoleName;
import org.example.entity.Role;
import org.example.entity.RoleName;
import org.example.repository.AdditionalRoleRepository;
import org.example.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Makes sure the reference rows every other feature depends on exist before the
 * first request. Idempotent: rows are only inserted when missing, so restarts and
 * a persistent database are both safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final Map<RoleName, String> ROLE_DESCRIPTIONS = new EnumMap<>(Map.of(
            RoleName.USER, "Standard application user",
            RoleName.PREMIUM, "User who has connected an email address and upgraded"));

    private static final Map<AdditionalRoleName, String> ADDITIONAL_ROLE_DESCRIPTIONS =
            new EnumMap<>(Map.of(
                    AdditionalRoleName.SELECT_ROLE, "May read rows from the shared database",
                    AdditionalRoleName.INSERT_ROLE, "May insert rows into the shared database",
                    AdditionalRoleName.UPDATE_ROLE, "May update rows in the shared database",
                    AdditionalRoleName.DELETE_ROLE, "May delete rows from the shared database"));

    private final RoleRepository roleRepository;
    private final AdditionalRoleRepository additionalRoleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int seeded = 0;
        for (RoleName name : RoleName.values()) {
            if (!roleRepository.existsByName(name)) {
                Role role = new Role();
                role.setName(name);
                role.setDescription(ROLE_DESCRIPTIONS.get(name));
                roleRepository.save(role);
                seeded++;
            }
        }
        for (AdditionalRoleName name : AdditionalRoleName.values()) {
            if (!additionalRoleRepository.existsByName(name)) {
                AdditionalRole additionalRole = new AdditionalRole();
                additionalRole.setName(name);
                additionalRole.setDescription(ADDITIONAL_ROLE_DESCRIPTIONS.get(name));
                additionalRoleRepository.save(additionalRole);
                seeded++;
            }
        }
        log.info("Reference data ready: {} role(s) seeded, {} application role(s) and {} database permission(s) total",
                seeded, roleRepository.count(), additionalRoleRepository.count());
    }
}
