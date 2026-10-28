package com.dormitory.service;

import com.dormitory.entity.StudentHabitProfile;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AllocationExplanationTest {
    private final SmartAllocationService service = new SmartAllocationService(null, null, null, null, null, null);

    @Test
    void explainsLargestDifferencesAndHandlesSingletonGroups() {
        StudentHabitProfile early = new StudentHabitProfile(); early.setScheduleScore(1); early.setAtmosphereScore(3);
        StudentHabitProfile late = new StudentHabitProfile(); late.setScheduleScore(5); late.setAtmosphereScore(3);
        String explanation = service.explainGroup(List.of(early, late));
        assertTrue(explanation.contains("作息"));
        assertTrue(explanation.contains("4"));
        assertTrue(service.explainGroup(List.of(early)).contains("单人"));
    }
}
