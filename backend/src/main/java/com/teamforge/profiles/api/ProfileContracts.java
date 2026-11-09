package com.teamforge.profiles.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.util.List;

public final class ProfileContracts {
    private ProfileContracts() {}
    public record Window(
        @NotNull DayOfWeek day,
        @NotNull @Pattern(regexp = "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$") String start,
        @NotNull @Pattern(regexp = "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$") String end) {}
    public record Update(
        @Size(min = 1, max = 80) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String displayName,
        @Size(min = 1, max = 64) String timeZone,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> skills,
        @Size(max = 28) List<@NotNull @Valid Window> collaborationWindows) {}
    public record Profile(String displayName, String timeZone, List<String> skills,
                          List<Window> collaborationWindows) {}
}
