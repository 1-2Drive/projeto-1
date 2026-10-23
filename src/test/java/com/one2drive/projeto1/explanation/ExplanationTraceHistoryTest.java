package com.one2drive.projeto1.explanation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplanationTraceHistoryTest {
    @Test
    void zeroCapacityRetainsCompleteSessionTrace() {
        ExplanationTrace trace = new ExplanationTrace(0);
        for (int i = 0; i < 1000; i++) {
            trace.add(i, TimelineEntryType.SENSOR, "sample " + i);
        }
        assertEquals(1000, trace.snapshot().size());
        assertEquals("sample 0", trace.snapshot().get(0).getMessage());
        assertEquals("sample 999", trace.snapshot().get(trace.snapshot().size() - 1).getMessage());
    }
}
