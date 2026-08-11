package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse create(UserRequest request) {
        // TODO: map request -> User, hash the password, persist, map back to UserResponse
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to UserResponse
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<UserResponse> list() {
        // TODO: load all users and map to UserResponse
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserResponse update(Long id, UserRequest request) {
        // TODO: load by id, apply the mutable fields from request, save, map to UserResponse
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id (or soft-delete once that is decided)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
