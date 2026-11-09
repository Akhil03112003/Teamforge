package com.teamforge.profiles.persistence;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class ProfileEntity {
    @Id @Column(name = "user_id")
    private UUID userId;
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "UTC";
    @Version
    private Long version;
    @ElementCollection
    @CollectionTable(name = "profile_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill", nullable = false, length = 40)
    private Set<String> skills = new LinkedHashSet<>();
    @ElementCollection
    @CollectionTable(name = "profile_availability", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "slot_order")
    private List<AvailabilitySlot> collaborationWindows = new ArrayList<>();

    protected ProfileEntity() {}
    public ProfileEntity(UUID userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }
    public UUID userId() { return userId; }
    public String displayName() { return displayName; }
    public void displayName(String value) { displayName = value; }
    public String timeZone() { return timeZone; }
    public void timeZone(String value) { timeZone = value; }
    public Set<String> skills() { return skills; }
    public List<AvailabilitySlot> collaborationWindows() { return collaborationWindows; }
}
