package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null once the account that acted was deleted: the entry stays, who it was does not. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "db_connection_id")
    private DbConnectionDetails dbConnection;

    @Column(nullable = false)
    private String action;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @PrePersist
    void prePersist() {
        if (performedAt == null) {
            performedAt = Instant.now();
        }
    }
}
