package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AccountDeletionRequest;
import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.service.AuthService;
import org.example.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "2. Users", description = "Accounts. Reads are open to any signed-in user; writes are limited to your own account")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable("id") Long id,
                                               @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete your own account",
            description = "Send the account's password in the body. The account goes for good, and with it "
                    + "its databases (stored passwords included), every share it gave or received, its "
                    + "premium link and profile; the username can be registered again. Audit entries stay, "
                    + "without the account. The token used for this call stops working at once.")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id,
                                       @Valid @RequestBody AccountDeletionRequest request,
                                       @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        userService.delete(id, request);
        authService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }
}
