package com.teamforge.profiles.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.profiles.api.ProfileContracts.*;
import com.teamforge.profiles.persistence.*;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository profiles;
    public ProfileService(ProfileRepository profiles) { this.profiles = profiles; }

    @Transactional
    public Profile create(UUID id, String displayName) {
        var profile = profiles.saveAndFlush(new ProfileEntity(id, displayName));
        return response(profile);
    }
    @Transactional(readOnly = true)
    public Profile get(UUID id) { return response(find(id)); }

    @Transactional
    public Profile update(UUID id, Update request) {
        var profile = find(id);
        if (request.displayName() != null) {
            if (request.displayName().isBlank()) invalid("Display name must not be blank.");
            profile.displayName(request.displayName().strip());
        }
        if (request.timeZone() != null) {
            if (!ZoneId.getAvailableZoneIds().contains(request.timeZone())) invalid("Choose a named IANA time zone.");
            profile.timeZone(request.timeZone());
        }
        if (request.skills() != null) {
            profile.skills().clear();
            for (String raw : request.skills()) {
                String skill = raw.strip().toLowerCase(Locale.ROOT);
                if (!skill.matches("[a-z0-9][a-z0-9 +#./-]{0,39}")) invalid("Skills must be short text tags using letters, digits or + # . / -.");
                profile.skills().add(skill);
            }
        }
        if (request.collaborationWindows() != null) {
            var sorted = request.collaborationWindows().stream()
                .sorted(Comparator.comparing(Window::day).thenComparing(Window::start)).toList();
            Window previous = null;
            for (Window window : sorted) {
                if (window.end().compareTo(window.start()) <= 0) invalid("Availability must end after it starts on the same day.");
                if (previous != null && previous.day() == window.day()
                    && window.start().compareTo(previous.end()) < 0) invalid("Availability windows must not overlap.");
                previous = window;
            }
            profile.collaborationWindows().clear();
            sorted.forEach(window -> profile.collaborationWindows().add(new AvailabilitySlot(
                window.day(), LocalTime.parse(window.start()), LocalTime.parse(window.end()))));
        }
        profiles.flush();
        return response(profile);
    }
    private ProfileEntity find(UUID id) {
        return profiles.findById(id).orElseThrow(() -> new ApiException(404, "PROFILE_NOT_FOUND", "Profile not found."));
    }
    private Profile response(ProfileEntity profile) {
        return new Profile(profile.displayName(), profile.timeZone(),
            profile.skills().stream().sorted().toList(),
            profile.collaborationWindows().stream().map(slot -> new Window(
                slot.day(), slot.start().toString(), slot.end().toString())).toList());
    }
    private static void invalid(String message) { throw new ApiException(400, "VALIDATION_ERROR", message); }
}
