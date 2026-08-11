package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.InvitationRequest;
import org.example.dto.InvitationResponse;
import org.example.repository.AdditionalRoleRepository;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.repository.InvitationRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final AdditionalRoleRepository additionalRoleRepository;

    public InvitationResponse create(InvitationRequest request) {
        // TODO: resolve fromUser/toUser/dbConnection/additionalRole, set status PENDING, persist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public InvitationResponse get(Long id) {
        // TODO: load by id, throw a not-found exception if absent, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<InvitationResponse> list() {
        // TODO: load all invitations and map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public InvitationResponse update(Long id, InvitationRequest request) {
        // TODO: load by id, apply the new status (accept/decline/revoke), save, map to response
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void delete(Long id) {
        // TODO: delete by id
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
