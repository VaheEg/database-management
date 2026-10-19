package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.InvitationRequest;
import org.example.dto.InvitationResponse;
import org.example.dto.InvitationStatusRequest;
import org.example.service.InvitationService;
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
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "5. Sharing", description = "Grant another user one permission on one of your databases")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @Operation(summary = "Invite a user to one of your databases",
            description = "Starts as PENDING. Only a premium user who owns that database may send it. "
                    + "Name the recipient by toUsername or toUserId; one invitation grants one permission.")
    public ResponseEntity<InvitationResponse> create(@Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvitationResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(invitationService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<InvitationResponse>> list() {
        return ResponseEntity.ok(invitationService.list());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Answer or revoke an invitation",
            description = "status = ACCEPTED or DECLINED by the recipient, REVOKED by the sender.")
    public ResponseEntity<InvitationResponse> update(@PathVariable("id") Long id,
                                                     @Valid @RequestBody InvitationStatusRequest request) {
        return ResponseEntity.ok(invitationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        invitationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
