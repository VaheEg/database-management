package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "4. Databases", description = "Register your external databases — passwords are stored encrypted and never returned")
public class DbConnectionDetailsController {

    private final DbConnectionDetailsService dbConnectionDetailsService;

    @PostMapping
    @Operation(summary = "Register one of your external databases",
            description = "The owner is taken from your token. dbPassword is encrypted at rest "
                    + "and never appears in a response.")
    public ResponseEntity<DbConnectionDetailsResponse> create(@Valid @RequestBody DbConnectionDetailsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dbConnectionDetailsService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DbConnectionDetailsResponse> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(dbConnectionDetailsService.get(id));
    }

    @GetMapping
    @Operation(summary = "Databases you own, plus the ones shared with you")
    public ResponseEntity<List<DbConnectionDetailsResponse>> list() {
        return ResponseEntity.ok(dbConnectionDetailsService.list());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a database you own",
            description = "Leave dbPassword out to keep the password already stored.")
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
