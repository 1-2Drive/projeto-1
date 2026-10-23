package com.one2drive.projeto1.drools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.facts.LaneOccupancyState;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FactDescriptionTest {

    @Test
    @DisplayName("describes a time-to-collision measurement as readable sensor evidence")
    void describesTtcEvent() {
        assertEquals("Radar: TTC 0.87 s to v1",
            FactDescription.describe(new TTCEvent(1_000L, "front-radar", 0.87, "v1")));
    }

    @Test
    @DisplayName("shortens generated UUIDs so descriptions stay on one line")
    void shortensGeneratedIds() {
        assertEquals("Radar: TTC 0.87 s to #b072",
            FactDescription.describe(new TTCEvent(1_000L, "front-radar", 0.87,
                "59c5dc66-99b1-415b-ae41-768e24e0b072")));
    }

    @Test
    @DisplayName("describes an infinite time-to-collision as no closing target")
    void describesMissingTtc() {
        assertEquals("Radar: no closing target to none",
            FactDescription.describe(new TTCEvent(1_000L, "front-radar", Double.POSITIVE_INFINITY, "none")));
    }

    @Test
    @DisplayName("describes a perceived speed limit with its sign and distance")
    void describesSpeedLimitEvent() {
        assertEquals("Camera read speed limit 50 km/h (sign sign-1) at 35 m",
            FactDescription.describe(new SpeedLimitDetectedEvent(1_000L, "camera", 50, 35.0, "sign-1")));
    }

    @Test
    @DisplayName("reports an infinite lane distance as none instead of Infinity")
    void describesEmptyLane() {
        assertEquals("Lane LEFT: clear, nearest none",
            FactDescription.describe(new LaneOccupancyState(Lane.LEFT, false, Double.POSITIVE_INFINITY, 1_000L)));
    }

    @Test
    @DisplayName("summarises a decision without dumping the whole record")
    void describesDecision() {
        DrivingDecision decision = new DrivingDecision();
        decision.apply(DrivingAction.CONTINUE, 50.0, RiskLevel.LOW, 0, "Clear road", "Default cruise", 1_000L, null);

        assertEquals("Decision CONTINUE (LOW risk) → 50 km/h, rule Default cruise",
            FactDescription.describe(decision));
    }

    @Test
    @DisplayName("falls back to a compact form for unknown fact types")
    void describesUnknownFact() {
        Object unknown = new Object() {
            @Override
            public String toString() {
                return "SomethingNew{a=1, b=2}";
            }
        };
        assertEquals("SomethingNew — a=1, b=2", FactDescription.describe(unknown));
    }
}