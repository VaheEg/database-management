package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.UserInformationRequest;
import org.example.dto.UserInformationResponse;
import org.example.repository.UserInformationRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserInformationService {

    private final UserInformationRepository userInformationRepository;
    private final UserRepository userRepository;

    public UserInformationResponse create(UserInformationRequest request) {
        // TODO: resolve the User by request.userId, map request -> UserInformation, persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserInformationResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<UserInformationResponse> list() {
        // TODO: load all profiles and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UserInformationResponse update(Long id, UserInformationRequest request) {
        // TODO: load by id, apply profile fields from request, save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
