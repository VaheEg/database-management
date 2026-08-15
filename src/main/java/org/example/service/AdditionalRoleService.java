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
