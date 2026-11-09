package com.teamforge.teams.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamContracts {
    private TeamContracts() {}
    public record Create(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String name,
        @Size(max = 2000) String description,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> desiredSkills) {}
    public record Update(
        @Size(min = 1, max = 120) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String name,
        @Size(max = 2000) String description,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> desiredSkills) {}
    public record Hackathon(UUID id, String name, int minSize, int maxSize, boolean active) {}
    public record Member(UUID userId, String displayName, Instant joinedAt, boolean leader) {}
    public record Team(UUID id, UUID hackathonId, String name, String description,
        List<String> desiredSkills, UUID leaderId, String status, int minSize, int maxSize,
        List<Member> members) {}
    public record Summary(UUID id, String name, String description, UUID leaderId,
        String status, int minSize, int maxSize, int memberCount) {}
    public record Board(List<Summary> items, UUID nextCursor) {}
    public record Leave(UUID teamId, boolean deleted, UUID leaderId) {}
    public record Audit(UUID id, UUID actorId, String action, String targetType,
                        UUID targetId, Instant timestamp) {}
    public record Items<T>(List<T> items) {}
}
