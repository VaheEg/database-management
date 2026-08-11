package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.DbConnectionDetailsRequest;
import org.example.dto.DbConnectionDetailsResponse;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DbConnectionDetailsService {

    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final UserRepository userRepository;

    public DbConnectionDetailsResponse create(DbConnectionDetailsRequest request) {
        // TODO: resolve the owner User, map request -> entity (encrypt dbPassword), persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public DbConnectionDetailsResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<DbConnectionDetailsResponse> list() {
        // TODO: load all connections and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public DbConnectionDetailsResponse update(Long id, DbConnectionDetailsRequest request) {
        // TODO: load by id, apply connection fields, save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
