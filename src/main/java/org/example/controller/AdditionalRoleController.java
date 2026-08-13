package org.example.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.AdditionalRoleRequest;
import org.example.dto.AdditionalRoleResponse;
import org.example.service.AdditionalRoleService;
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
@RequestMapping("/api/additional-roles")
@RequiredArgsConstructor
@Tag(name = "8. Database permissions", description = "SELECT_ROLE, INSERT_ROLE, UPDATE_ROLE, DELETE_ROLE — the values an invitation grants")
public class AdditionalRoleController {

    private final AdditionalRoleService additionalRoleService;

    @PostMapping
    public ResponseEntity<AdditionalRoleResponse> create(@Valid @RequestBody AdditionalRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(additionalRoleService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdditionalRoleResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(additionalRoleService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<AdditionalRoleResponse>> list() {
        return ResponseEntity.ok(additionalRoleService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdditionalRoleResponse> update(@PathVariable("id") Long id,
                                                         @Valid @RequestBody AdditionalRoleRequest request) {
        return ResponseEntity.ok(additionalRoleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        additionalRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
