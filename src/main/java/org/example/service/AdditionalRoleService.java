package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.AdditionalRoleRequest;
import org.example.dto.AdditionalRoleResponse;
import org.example.repository.AdditionalRoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdditionalRoleService {

    private final AdditionalRoleRepository additionalRoleRepository;

    public AdditionalRoleResponse create(AdditionalRoleRequest request) {
        // TODO: map request -> AdditionalRole, reject duplicates by name, persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AdditionalRoleResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<AdditionalRoleResponse> list() {
        // TODO: load all permissions and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AdditionalRoleResponse update(Long id, AdditionalRoleRequest request) {
        // TODO: load by id, apply name/description, save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
