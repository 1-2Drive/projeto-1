package com.one2drive.projeto1.drools.listeners;

import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;

public class WorkingMemoryTraceListener extends DefaultRuleRuntimeEventListener {
    private final ExplanationTrace trace;

    public WorkingMemoryTraceListener(ExplanationTrace trace) {
        this.trace = trace;
    }

    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        trace.add(
            event.getKieRuntime().getSessionClock().getCurrentTime(),
            TimelineEntryType.FACT_INSERTED,
            "FACT INSERTED " + FactDescription.describe(event.getObject())
        );
    }

    @Override
    public void objectUpdated(ObjectUpdatedEvent event) {
        trace.add(
            event.getKieRuntime().getSessionClock().getCurrentTime(),
            TimelineEntryType.FACT_UPDATED,
            "FACT UPDATED " + FactDescription.describe(event.getObject())
        );
    }

    @Override
    public void objectDeleted(ObjectDeletedEvent event) {
        trace.add(
            event.getKieRuntime().getSessionClock().getCurrentTime(),
            TimelineEntryType.FACT_DELETED,
            "FACT DELETED " + FactDescription.describe(event.getOldObject())
        );
    }
}
