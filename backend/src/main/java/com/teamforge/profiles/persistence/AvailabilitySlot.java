package com.teamforge.profiles.persistence;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Embeddable
public class AvailabilitySlot {
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek day;
    @Column(name = "start_time", nullable = false, columnDefinition = "time(0)")
    private LocalTime start;
    @Column(name = "end_time", nullable = false, columnDefinition = "time(0)")
    private LocalTime end;
    protected AvailabilitySlot() {}
    public AvailabilitySlot(DayOfWeek day, LocalTime start, LocalTime end) {
        this.day = day;
        this.start = start;
        this.end = end;
    }
    public DayOfWeek day() { return day; }
    public LocalTime start() { return start; }
    public LocalTime end() { return end; }
}
