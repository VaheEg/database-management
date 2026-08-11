package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.InvitationRequest;
import org.example.dto.InvitationResponse;
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
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
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
    public ResponseEntity<InvitationResponse> update(@PathVariable("id") Long id,
                                                     @Valid @RequestBody InvitationRequest request) {
        return ResponseEntity.ok(invitationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        invitationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
