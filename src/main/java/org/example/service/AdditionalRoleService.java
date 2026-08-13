package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.AdditionalRoleRequest;
import org.example.dto.AdditionalRoleResponse;
import org.example.entity.AdditionalRole;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.AdditionalRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Database-scoped permissions (SELECT_ROLE, INSERT_ROLE, UPDATE_ROLE, DELETE_ROLE) —
 * the values an invitation grants. Reference data seeded at startup; read these to
 * find the {@code additionalRoleId} to put in an invitation.
 */
@Service
@RequiredArgsConstructor
public class AdditionalRoleService {

    private final AdditionalRoleRepository additionalRoleRepository;

    @Transactional
    public AdditionalRoleResponse create(AdditionalRoleRequest request) {
        if (additionalRoleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Permission " + request.getName() + " already exists");
        }
        AdditionalRole additionalRole = new AdditionalRole();
        additionalRole.setName(request.getName());
        additionalRole.setDescription(request.getDescription());
        return toResponse(additionalRoleRepository.save(additionalRole));
    }

    @Transactional(readOnly = true)
    public AdditionalRoleResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AdditionalRoleResponse> list() {
        return additionalRoleRepository.findAll().stream()
                .map(AdditionalRoleService::toResponse)
                .toList();
    }

    @Transactional
    public AdditionalRoleResponse update(Long id, AdditionalRoleRequest request) {
        AdditionalRole additionalRole = findOrThrow(id);
        if (!additionalRole.getName().equals(request.getName())
                && additionalRoleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Permission " + request.getName() + " already exists");
        }
        additionalRole.setName(request.getName());
        additionalRole.setDescription(request.getDescription());
        return toResponse(additionalRoleRepository.save(additionalRole));
    }

    /**
     * Deleting a permission that invitations still reference is rejected by the
     * foreign key and surfaces as 409 Conflict.
     */
    @Transactional
    public void delete(Long id) {
        additionalRoleRepository.delete(findOrThrow(id));
    }

    private AdditionalRole findOrThrow(Long id) {
        return additionalRoleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Permission", id));
    }

    private static AdditionalRoleResponse toResponse(AdditionalRole additionalRole) {
        return AdditionalRoleResponse.builder()
                .id(additionalRole.getId())
                .name(additionalRole.getName())
                .description(additionalRole.getDescription())
                .build();
    }
}
