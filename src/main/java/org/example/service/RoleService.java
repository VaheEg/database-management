package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.RoleRequest;
import org.example.dto.RoleResponse;
import org.example.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleResponse create(RoleRequest request) {
        // TODO: map request -> Role, reject duplicates by name, persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public RoleResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<RoleResponse> list() {
        // TODO: load all roles and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public RoleResponse update(Long id, RoleRequest request) {
        // TODO: load by id, apply name/description, save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
