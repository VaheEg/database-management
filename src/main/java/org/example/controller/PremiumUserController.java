package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.PremiumUserRequest;
import org.example.dto.PremiumUserResponse;
import org.example.service.PremiumUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/premium-users")
@RequiredArgsConstructor
@Tag(name = "9. Premium", description = "Connect an email address to upgrade your account")
public class PremiumUserController {

    private final PremiumUserService premiumUserService;

    @PostMapping
    @Operation(summary = "Connect an email and become premium",
            description = "Adds the PREMIUM role. Log in again for a token that carries it.")
    public ResponseEntity<PremiumUserResponse> create(@Valid @RequestBody PremiumUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(premiumUserService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PremiumUserResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(premiumUserService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<PremiumUserResponse>> list() {
        return ResponseEntity.ok(premiumUserService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<PremiumUserResponse> update(@PathVariable("id") Long id,
                                                      @Valid @RequestBody PremiumUserRequest request) {
        return ResponseEntity.ok(premiumUserService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        premiumUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
