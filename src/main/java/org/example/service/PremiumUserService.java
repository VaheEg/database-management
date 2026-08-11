package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.PremiumUserRequest;
import org.example.dto.PremiumUserResponse;
import org.example.repository.PremiumUserRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PremiumUserService {

    private final PremiumUserRepository premiumUserRepository;
    private final UserRepository userRepository;

    public PremiumUserResponse create(PremiumUserRequest request) {
        // TODO: resolve the User, link the connected email, persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PremiumUserResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<PremiumUserResponse> list() {
        // TODO: load all premium links and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PremiumUserResponse update(Long id, PremiumUserRequest request) {
        // TODO: load by id, apply the connected email, save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id (revokes premium status)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
