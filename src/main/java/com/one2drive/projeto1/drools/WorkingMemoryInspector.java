package com.one2drive.projeto1.drools;

import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;

import java.util.ArrayList;
import java.util.List;

public class WorkingMemoryInspector {
    private final KieSession kieSession;

    public WorkingMemoryInspector(KieSession kieSession) {
        this.kieSession = kieSession;
    }

    public List<String> snapshotFacts() {
        List<String> rows = new ArrayList<>();
        kieSession.getObjects().forEach(o -> rows.add(o.toString()));
        return rows;
    }

    public List<String> queryCurrentDecision() {
        QueryResults results = kieSession.getQueryResults("Current Driving Decision");
        List<String> rows = new ArrayList<>();
        results.forEach(row -> rows.add(String.valueOf(row.get("$decision"))));
        return rows;
    }
}
