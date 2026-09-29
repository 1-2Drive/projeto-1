package com.one2drive.projeto1.explanation;

import com.one2drive.projeto1.decision.DrivingDecision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ExplanationTrace {
    private final LinkedList<TimelineEntry> timeline;
    private final int maxEntries;

    public ExplanationTrace(int maxEntries) {
        this.timeline = new LinkedList<>();
        this.maxEntries = maxEntries;
    }

    public synchronized void add(long timestamp, TimelineEntryType type, String message) {
        timeline.add(new TimelineEntry(timestamp, type, message));
        while (timeline.size() > maxEntries) {
            timeline.removeFirst();
        }
    }

    public synchronized void addDecision(DrivingDecision decision) {
        add(
            decision.getTimestamp(),
            TimelineEntryType.DECISION,
            decision.getAction() + " reason=" + decision.getReason() + " rule=" + decision.getSourceRule()
        );
    }

    public synchronized List<TimelineEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(timeline));
    }
}
