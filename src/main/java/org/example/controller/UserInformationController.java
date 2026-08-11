package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.UserInformationRequest;
import org.example.dto.UserInformationResponse;
import org.example.service.UserInformationService;
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
@RequestMapping("/api/user-information")
@RequiredArgsConstructor
public class UserInformationController {

    private final UserInformationService userInformationService;

    @PostMapping
    public ResponseEntity<UserInformationResponse> create(@Valid @RequestBody UserInformationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userInformationService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserInformationResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userInformationService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<UserInformationResponse>> list() {
        return ResponseEntity.ok(userInformationService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserInformationResponse> update(@PathVariable("id") Long id,
                                                          @Valid @RequestBody UserInformationRequest request) {
        return ResponseEntity.ok(userInformationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userInformationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
