package com.teamforge.events.persistence;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Foundational event mapping; no event management API is exposed in M1-A. */
@Entity
@Table(name = "hackathons")
public class HackathonEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "minimum_team_size", nullable = false)
    private int minimumTeamSize;

    @Column(name = "maximum_team_size", nullable = false)
    private int maximumTeamSize;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected HackathonEntity() {
        // For JPA. Event creation belongs to a later milestone.
    }
}
