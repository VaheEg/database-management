package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.UserInformationRequest;
import org.example.dto.UserInformationResponse;
import org.example.entity.User;
import org.example.entity.UserInformation;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.UserInformationRepository;
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The optional profile attached to an account. One row per user, and only ever
 * your own: profiles hold personal details, so there is no listing of everyone's.
 */
@Service
@RequiredArgsConstructor
public class UserInformationService {

    private final UserInformationRepository userInformationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public UserInformationResponse create(UserInformationRequest request) {
        Long userId = currentUserService.requireId();
        if (request.getUserId() != null && !request.getUserId().equals(userId)) {
            currentUserService.requireSelf(request.getUserId(), "create a profile for yourself");
        }
        if (userInformationRepository.existsByUserId(userId)) {
            throw new BadRequestException(
                    "A profile already exists for this account — update it instead of creating a second one");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        UserInformation information = new UserInformation();
        information.setUser(user);
        apply(request, information);
        return toResponse(userInformationRepository.save(information));
    }

    @Transactional(readOnly = true)
    public UserInformationResponse get(Long id) {
        return toResponse(findOwnOrThrow(id));
    }

    /** Your own profile, as a list of zero or one, so the endpoint shape stays uniform. */
    @Transactional(readOnly = true)
    public List<UserInformationResponse> list() {
        return userInformationRepository.findByUserId(currentUserService.requireId())
                .map(UserInformationService::toResponse)
                .map(List::of)
                .orElseGet(List::of);
    }

    @Transactional
    public UserInformationResponse update(Long id, UserInformationRequest request) {
        UserInformation information = findOwnOrThrow(id);
        apply(request, information);
        return toResponse(userInformationRepository.save(information));
    }

    @Transactional
    public void delete(Long id) {
        userInformationRepository.delete(findOwnOrThrow(id));
    }

    private UserInformation findOwnOrThrow(Long id) {
        UserInformation information = userInformationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Profile", id));
        currentUserService.requireSelf(information.getUser().getId(), "read or change your own profile");
        return information;
    }

    private static void apply(UserInformationRequest request, UserInformation information) {
        information.setFirstName(request.getFirstName());
        information.setLastName(request.getLastName());
        information.setPhoneNumber(request.getPhoneNumber());
        information.setDateOfBirth(request.getDateOfBirth());
        information.setCountry(request.getCountry());
        information.setCity(request.getCity());
        information.setAvatarUrl(request.getAvatarUrl());
    }

    private static UserInformationResponse toResponse(UserInformation information) {
        return UserInformationResponse.builder()
                .id(information.getId())
                .userId(information.getUser().getId())
                .firstName(information.getFirstName())
                .lastName(information.getLastName())
                .phoneNumber(information.getPhoneNumber())
                .dateOfBirth(information.getDateOfBirth())
                .country(information.getCountry())
                .city(information.getCity())
                .avatarUrl(information.getAvatarUrl())
                .build();
    }
}
