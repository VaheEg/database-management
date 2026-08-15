package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.RoleRequest;
import org.example.dto.RoleResponse;
import org.example.entity.Role;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Role " + request.getName() + " already exists");
        }
        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        return toResponse(roleRepository.save(role));
    }

    @Transactional(readOnly = true)
    public RoleResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(RoleService::toResponse).toList();
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        Role role = findOrThrow(id);
        if (!role.getName().equals(request.getName()) && roleRepository.existsByName(request.getName())) {
            throw new BadRequestException("Role " + request.getName() + " already exists");
        }
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        Role role = findOrThrow(id);
        if (!role.getUserRoles().isEmpty()) {
            throw new BadRequestException(
                    "Role " + role.getName() + " is still assigned to " + role.getUserRoles().size() + " user(s)");
        }
        roleRepository.delete(role);
    }

    private Role findOrThrow(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Role", id));
    }

    private static RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .build();
    }
}
