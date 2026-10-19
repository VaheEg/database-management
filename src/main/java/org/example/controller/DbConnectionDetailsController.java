package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.DbConnectionDetailsRequest;
import org.example.dto.DbConnectionDetailsResponse;
import org.example.dto.MessageResponse;
import org.example.service.DbConnectionDetailsService;
import org.example.service.QueryExecutionService;
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
    private final QueryExecutionService queryExecutionService;

    @PostMapping
    @Operation(summary = "Register one of your external databases",
            description = "The owner is taken from your token. dbPassword is encrypted at rest "
                    + "and never appears in a response.")
    public ResponseEntity<DbConnectionDetailsResponse> create(@Valid @RequestBody DbConnectionDetailsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dbConnectionDetailsService.create(request));
    }

    @PostMapping("/test")
    @Operation(summary = "Check that a database can be reached, before registering it",
            description = "Connects with the details sent and saves nothing. 200 says what answered; "
                    + "502 says why the database could not be reached.")
    public ResponseEntity<MessageResponse> testUnsaved(@Valid @RequestBody DbConnectionDetailsRequest request) {
        return ResponseEntity.ok(queryExecutionService.testUnsaved(request));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Check that one of your databases can be reached",
            description = "Without a body, tests the saved settings. With one, tests those settings instead — "
                    + "leave dbPassword out to use the stored password — so an edit can be tried before "
                    + "it is saved. Owner only; nothing is saved.")
    public ResponseEntity<MessageResponse> testSaved(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) DbConnectionDetailsRequest request) {
        return ResponseEntity.ok(queryExecutionService.testSaved(id, request));
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
    @Operation(summary = "Remove one of your databases from your account",
            description = "Only the saved connection goes — the database itself is not touched. Its "
                    + "invitations go with it, so whoever it was shared with loses access; the audit "
                    + "trail stays.")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        dbConnectionDetailsService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
