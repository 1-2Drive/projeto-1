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
        while (maxEntries > 0 && timeline.size() > maxEntries) {
            timeline.removeFirst();
        }
    }

    public synchronized void addDecision(DrivingDecision decision) {
        add(
            decision.getTimestamp(),
            TimelineEntryType.DECISION,
            decision.getSpeedAction() + " reason=" + decision.getSpeedReason() + " rule=" + decision.getSpeedSourceRule()
                + (decision.isLaneManeuver()
                    ? " | lane " + decision.getLaneAction() + " rule=" + decision.getLaneSourceRule() : "")
        );
    }

    public synchronized List<TimelineEntry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(timeline));
    }
}
