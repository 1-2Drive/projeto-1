package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.drools.facts.FuzzyConclusion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzySupportTest {

    @Test
    void inputTermsAddUpToOneEverywhere() {
        for (int i = 0; i <= 100; i++) {
            double x = i / 100.0;
            double sum = FuzzySupport.INPUT_TERMS.stream().mapToDouble(t -> FuzzySupport.inputDegree(t, x)).sum();
            assertEquals(1.0, sum, 1e-9, "degrees at " + x);
        }
    }

    @Test
    void inputDegreesAtKnownPoints() {
        assertEquals(1.0, FuzzySupport.inputDegree("poor", 0.2), 1e-9);
        assertEquals(0.75, FuzzySupport.inputDegree("reduced", 0.55), 1e-9);
        assertEquals(0.25, FuzzySupport.inputDegree("fair", 0.55), 1e-9);
        assertEquals(1.0, FuzzySupport.inputDegree("fair", 0.7), 1e-9);
        assertEquals(0.0, FuzzySupport.inputDegree("fair", 0.85), 1e-9, "no restrictive term from 0.85 up");
        assertEquals(1.0, FuzzySupport.inputDegree("good", 0.85), 1e-9);
        assertEquals(1.0, FuzzySupport.inputDegree("good", 1.2), 1e-9, "readings are clamped to 0..1");
    }

    @Test
    void centroidOfOneFullTermIsItsPeak() {
        assertEquals(25.0, FuzzySupport.centroid(List.of(new FuzzyConclusion("very_slow", 1.0))), 1e-9);
        assertEquals(55.0, FuzzySupport.centroid(List.of(new FuzzyConclusion("moderate", 1.0))), 1e-9);
        assertEquals(65.0, FuzzySupport.centroid(List.of(new FuzzyConclusion("prudent", 1.0))), 1e-9);
    }

    @Test
    void centroidCombinesClippedTerms() {
        double speed = FuzzySupport.centroid(List.of(
            new FuzzyConclusion("slow", 0.75), new FuzzyConclusion("moderate", 0.25)));
        assertEquals(44.64, speed, 0.01);
    }

    @Test
    void lightAloneDoesNotRestrict() {
        assertFalse(FuzzySupport.restricts(List.of(new FuzzyConclusion("light", 1.0))));
        assertTrue(FuzzySupport.restricts(List.of(
            new FuzzyConclusion("light", 0.9), new FuzzyConclusion("moderate", 0.1))));
    }

    @Test
    void strongestTermBreaksTiesTowardsTheMoreRestrictive() {
        assertEquals("slow", FuzzySupport.strongestTerm(List.of(
            new FuzzyConclusion("moderate", 0.5), new FuzzyConclusion("slow", 0.5))));
        assertEquals("prudent", FuzzySupport.strongestTerm(List.of(
            new FuzzyConclusion("moderate", 0.27), new FuzzyConclusion("prudent", 0.73))));
    }
}
