package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "premium_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PremiumUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "connected_email", nullable = false, unique = true, length = 255)
    private String connectedEmail;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @PrePersist
    void prePersist() {
        if (activatedAt == null) {
            activatedAt = Instant.now();
        }
    }
}
