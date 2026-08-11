package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.DbConnectionDetailsRequest;
import org.example.dto.DbConnectionDetailsResponse;
import org.example.service.DbConnectionDetailsService;
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
@RequestMapping("/api/db-connections")
@RequiredArgsConstructor
public class DbConnectionDetailsController {

    private final DbConnectionDetailsService dbConnectionDetailsService;

    @PostMapping
    public ResponseEntity<DbConnectionDetailsResponse> create(@Valid @RequestBody DbConnectionDetailsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dbConnectionDetailsService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionDetailsResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(dbConnectionDetailsService.get(id));
    }

    @GetMapping
    public ResponseEntity<List<DbConnectionDetailsResponse>> list() {
        return ResponseEntity.ok(dbConnectionDetailsService.list());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DbConnectionDetailsResponse> update(@PathVariable("id") Long id,
                                                              @Valid @RequestBody DbConnectionDetailsRequest request) {
        return ResponseEntity.ok(dbConnectionDetailsService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        dbConnectionDetailsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
