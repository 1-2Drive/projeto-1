package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.drools.facts.OvertakingStateFact;
import com.one2drive.projeto1.inference.ExpertBackendType;
import org.kie.api.runtime.ClassObjectFilter;

/**
 * The overtaking state as the active backend holds it, so one test can check it under either
 * engine: Drools keeps it in an OvertakingStateFact, Prolog reports the state each cycle ended
 * with as {@code overtaking_result(Active, Origin, Passed, Start, Target)}.
 */
final class OvertakingProbe {

    record OvertakingView(boolean active, String targetId, boolean passedTarget) {
    }

    private OvertakingProbe() {
    }

    static OvertakingView of(SimulationEngine engine) {
        if (engine.getBackendType() == ExpertBackendType.DROOLS) {
            OvertakingStateFact fact = (OvertakingStateFact) engine.getKnowledgeSession().getKieSession()
                .getObjects(new ClassObjectFilter(OvertakingStateFact.class)).iterator().next();
            return new OvertakingView(fact.isActive(), fact.getTargetId(), fact.isPassedTarget());
        }
        String fact = engine.getWorkingMemoryFactViews().stream()
            .map(WorkingMemoryFactView::value)
            .filter(value -> value.startsWith("overtaking_result("))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no overtaking_result fact in the Prolog working memory"));
        String[] args = fact.substring(fact.indexOf('(') + 1, fact.lastIndexOf(')')).split(",");
        return new OvertakingView(Boolean.parseBoolean(args[0]), args[4], Boolean.parseBoolean(args[2]));
    }
}
