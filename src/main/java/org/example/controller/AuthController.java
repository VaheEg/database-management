package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AuthResponse;
import org.example.dto.CurrentUserResponse;
import org.example.dto.LoginRequest;
import org.example.dto.MessageResponse;
import org.example.dto.RegisterRequest;
import org.example.security.AppUserDetails;
import org.example.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Get a token, check it, throw it away")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Create an account and receive a JWT",
            description = "Assigns the default USER role. The returned token is ready to paste into Authorize.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange username and password for a JWT",
            description = "Returns 401 for both a wrong password and an unknown username.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Who am I?", description = "Returns the id, username and roles carried by the token.")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AppUserDetails principal) {
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(CurrentUserResponse.builder()
                .id(principal.getId())
                .username(principal.getUsername())
                .roles(roles)
                .build());
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke the token used for this request",
            description = """
                    The token stops being accepted immediately — call anything with it \
                    afterwards and you get 401. Other sessions of the same user keep their \
                    own tokens. Nothing to send in the body; the token in the Authorization \
                    header is the one revoked.""")
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok(new MessageResponse("Logged out — this token is no longer valid"));
    }
}
