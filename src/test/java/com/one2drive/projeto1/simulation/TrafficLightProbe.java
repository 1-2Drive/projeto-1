package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.inference.ExpertBackendType;

import java.util.List;

/**
 * The traffic light the active backend holds, rendered as Drools' TrafficLightStateFact
 * prints itself, so one test can read it under either engine. Prolog holds the same
 * knowledge as {@code traffic_light(Id, State, Distance)} and
 * {@code light_commitment(Id, stop|proceed)} in the cycle's working memory.
 */
final class TrafficLightProbe {

    private TrafficLightProbe() {
    }

    static String lightFact(SimulationEngine engine) {
        List<WorkingMemoryFactView> facts = engine.getWorkingMemoryFactViews();
        if (engine.getBackendType() == ExpertBackendType.DROOLS) {
            return facts.stream().filter(f -> f.type().equals("TrafficLightStateFact"))
                .map(WorkingMemoryFactView::value).findFirst().orElse("");
        }
        String[] light = facts.stream().map(WorkingMemoryFactView::value)
            .filter(value -> value.startsWith("traffic_light("))
            .map(TrafficLightProbe::args).findFirst().orElse(null);
        if (light == null) {
            return "TrafficLightStateFact{lightId='none', state=GREEN, distanceMeters=Infinity, commitment=NONE}";
        }
        String commitment = facts.stream().map(WorkingMemoryFactView::value)
            .filter(value -> value.startsWith("light_commitment(" + light[0] + ","))
            .map(value -> args(value)[1])
            .map(c -> c.equals("stop") ? "COMMITTED_TO_STOP" : "COMMITTED_TO_PROCEED")
            .findFirst().orElse("NONE");
        return "TrafficLightStateFact{lightId='" + light[0] + "', state=" + light[1].toUpperCase()
            + ", distanceMeters=" + light[2] + ", commitment=" + commitment + "}";
    }

    private static String[] args(String fact) {
        return fact.substring(fact.indexOf('(') + 1, fact.lastIndexOf(')')).split(",");
    }
}
