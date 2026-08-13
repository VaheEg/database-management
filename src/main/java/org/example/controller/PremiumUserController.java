package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.MessageResponse;
import org.example.dto.PremiumUserConfirmRequest;
import org.example.dto.PremiumUserRequest;
import org.example.dto.PremiumUserResponse;
import org.example.service.PremiumUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/premium-users")
@RequiredArgsConstructor
@Tag(name = "9. Premium", description = "Verify an email address to upgrade your account")
public class PremiumUserController {

    private final PremiumUserService premiumUserService;

    @PostMapping("/request-code")
    @Operation(summary = "Send a verification code to an email address",
            description = "Step 1 of 2. Nothing changes on the account yet — the code has to come "
                    + "back through /confirm. Use the same call to move an already-connected "
                    + "address to a new one.")
    public ResponseEntity<MessageResponse> requestCode(@Valid @RequestBody PremiumUserRequest request) {
        return ResponseEntity.ok(premiumUserService.requestCode(request));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm the code and become premium",
            description = "Step 2 of 2. Adds the PREMIUM role. Log in again for a token that carries it.")
    public ResponseEntity<PremiumUserResponse> confirm(@Valid @RequestBody PremiumUserConfirmRequest request) {
        return ResponseEntity.ok(premiumUserService.confirm(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PremiumUserResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(premiumUserService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<PremiumUserResponse>> list() {
        return ResponseEntity.ok(premiumUserService.list());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Disconnect the address and drop back to a normal account")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        premiumUserService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
