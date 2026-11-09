package com.teamforge.teams;

import com.teamforge.suggestions.SuggestionScoring;
import com.teamforge.suggestions.SuggestionScoring.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SuggestionScoringTest {
    private static final LocalDate MONDAY=LocalDate.of(2026,11,9);
    Profile profile(Set<String> skills,String zone,List<Window> windows){return new Profile(UUID.randomUUID(),"Candidate",skills,zone,windows);}
    BitSet reference(){return SuggestionScoring.availability(profile(Set.of(),"UTC",List.of()),MONDAY);}
    @Test void perfectComplementAndOverlapScore100(){var score=SuggestionScoring.score(Set.of("java"),reference(),profile(Set.of("java"),"UTC",List.of()),MONDAY);assertThat(score.score()).isEqualTo(100);}
    @Test void halfCoverageAndFullOverlapScore65(){var score=SuggestionScoring.score(Set.of("java","react"),reference(),profile(Set.of("java"),"UTC",List.of()),MONDAY);assertThat(score.skillScore()).isEqualTo(50);assertThat(score.timeScore()).isEqualTo(100);assertThat(score.score()).isEqualTo(65);}
    @Test void noCoverageButFullOverlapScores30(){assertThat(SuggestionScoring.score(Set.of("java"),reference(),profile(Set.of(),"UTC",List.of()),MONDAY).score()).isEqualTo(30);}
    @Test void missingDesiredSkillsScoresZeroSkills(){assertThat(SuggestionScoring.score(Set.of(),reference(),profile(Set.of("java"),"UTC",List.of()),MONDAY).skillScore()).isZero();}
    @Test void nullZoneScoresZeroOverlap(){assertThat(SuggestionScoring.score(Set.of(),reference(),profile(Set.of(),null,List.of()),MONDAY).timeScore()).isZero();}
    @Test void invalidZoneScoresZeroOverlap(){assertThat(SuggestionScoring.availability(profile(Set.of(),"invalid-zone",List.of()),MONDAY).isEmpty()).isTrue();}
    @Test void emptyReferenceScoresZeroOverlap(){assertThat(SuggestionScoring.score(Set.of(),new BitSet(),profile(Set.of(),"UTC",List.of()),MONDAY).timeScore()).isZero();}
    @Test void timeZonesAffectActualCollaborationOverlap(){var same=SuggestionScoring.score(Set.of(),reference(),profile(Set.of(),"UTC",List.of()),MONDAY);var distant=SuggestionScoring.score(Set.of(),reference(),profile(Set.of(),"Asia/Tokyo",List.of()),MONDAY);assertThat(same.timeScore()).isGreaterThan(distant.timeScore());assertThat(distant.timeScore()).isZero();}
    @Test void explicitAvailabilityOverridesDefaults(){var windows=List.of(new Window(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(13,0)));var score=SuggestionScoring.score(Set.of(),reference(),profile(Set.of(),"UTC",windows),MONDAY);assertThat(score.timeScore()).isEqualTo(10);}
    @Test void noDoubleCountingOverlappingIntervals(){var p=profile(Set.of(),"UTC",List.of(new Window(DayOfWeek.MONDAY,LocalTime.of(9,0),LocalTime.of(12,0)),new Window(DayOfWeek.MONDAY,LocalTime.of(10,0),LocalTime.of(13,0))));assertThat(SuggestionScoring.availability(p,MONDAY).cardinality()).isEqualTo(240);}
    @Test void daylightSavingGapUsesIanaRules(){var p=profile(Set.of(),"America/New_York",List.of(new Window(DayOfWeek.SUNDAY,LocalTime.of(1,0),LocalTime.of(4,0))));assertThat(SuggestionScoring.availability(p,LocalDate.of(2026,3,2)).cardinality()).isEqualTo(120);}
    @Test void deterministicComponentsAndExplanations(){var p=profile(Set.of("java","react"),"Asia/Kolkata",List.of());var a=SuggestionScoring.score(Set.of("java","go","react"),reference(),p,MONDAY);assertThat(SuggestionScoring.score(Set.of("react","java","go"),reference(),p,MONDAY)).isEqualTo(a);assertThat(a.score()).isEqualTo(Math.round((.7*a.skillScore()+.3*a.timeScore())*100)/100.0);}
}
