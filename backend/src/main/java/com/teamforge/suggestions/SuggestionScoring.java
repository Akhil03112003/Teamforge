package com.teamforge.suggestions;

import java.time.*;
import java.util.*;

public final class SuggestionScoring {
    private SuggestionScoring(){}
    public record Window(DayOfWeek day,LocalTime start,LocalTime end){}
    public record Profile(UUID id,String displayName,Set<String> skills,String zone,List<Window> windows){}
    public record Score(double score,double skillScore,double timeScore,List<String> reasons){}
    public static BitSet availability(Profile profile,LocalDate start) {
        var result=new BitSet(10080);
        if(profile.zone()==null)return result;
        ZoneId zone;try{zone=ZoneId.of(profile.zone());}catch(DateTimeException invalid){return result;}
        Instant origin=start.atStartOfDay(ZoneOffset.UTC).toInstant();
        for(int offset=-1;offset<=8;offset++){
            LocalDate date=start.plusDays(offset);
            List<Window> windows=profile.windows();
            if(windows.isEmpty())windows=List.of(new Window(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(17,0)),
                new Window(DayOfWeek.TUESDAY,LocalTime.of(9,0),LocalTime.of(17,0)),new Window(DayOfWeek.WEDNESDAY,LocalTime.of(9,0),LocalTime.of(17,0)),
                new Window(DayOfWeek.THURSDAY,LocalTime.of(9,0),LocalTime.of(17,0)),new Window(DayOfWeek.FRIDAY,LocalTime.of(9,0),LocalTime.of(17,0)));
            for(var window:windows)if(window.day()==date.getDayOfWeek()) {
                long from=Duration.between(origin,date.atTime(window.start()).atZone(zone).toInstant()).toMinutes();
                long to=Duration.between(origin,date.atTime(window.end()).atZone(zone).toInstant()).toMinutes();
                int lo=(int)Math.max(0,Math.min(10080,from)),hi=(int)Math.max(0,Math.min(10080,to));
                if(hi>lo)result.set(lo,hi);
            }
        }
        return result;
    }
    public static Score score(Set<String> missing,BitSet common,Profile candidate,LocalDate start){
        var coverage=new TreeSet<>(candidate.skills());coverage.retainAll(missing);
        double skill=missing.isEmpty()?0:100.0*coverage.size()/missing.size();
        BitSet overlap=availability(candidate,start);overlap.and(common);
        double time=common.isEmpty()?0:100.0*overlap.cardinality()/common.cardinality();
        skill=round(skill);time=round(time);
        return new Score(round(.7*skill+.3*time),skill,time,List.of(
            "Covers "+coverage.size()+" of "+missing.size()+" uncovered desired skills.",
            "Shares "+overlap.cardinality()+" of "+common.cardinality()+" team collaboration minutes in the seven-day UTC window.",
            "Missing availability uses weekdays 09:00–17:00 in the profile zone; unavailable zone data scores zero overlap."));
    }
    private static double round(double value){return Math.round(Math.max(0,Math.min(100,value))*100.0)/100.0;}
}
