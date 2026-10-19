package com.one2drive.projeto1.api.model;

import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.SimulationSession;

import java.util.List;

public record ApiWhyAnswer(String sessionId, long timestampMillis, String question, String selected,
                           List<String> why, List<String> whyNot, List<String> observations,
                           List<String> facts, List<String> rules, List<String> actuator,
                           List<SessionSnapshot.ProposalSnapshot> proposals,
                           List<SessionSnapshot.LaneCheckSnapshot> laneSafety,
                           String engine, List<SessionSnapshot> candidates,
                           List<HowStep> how) {
    /** One stage of the WORLD -> SENSORS -> WORKING MEMORY -> RULES -> DECISION -> ACTUATOR pipeline. */
    public record HowStep(String stage, List<String> evidence) { }
}
