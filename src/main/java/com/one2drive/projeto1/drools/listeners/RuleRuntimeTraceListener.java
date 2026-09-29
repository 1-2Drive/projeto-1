package com.one2drive.projeto1.drools.listeners;

import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;

public class RuleRuntimeTraceListener extends DefaultAgendaEventListener implements AgendaEventListener {
    private final ExplanationTrace trace;

    public RuleRuntimeTraceListener(ExplanationTrace trace) {
        this.trace = trace;
    }

    @Override
    public void matchCreated(MatchCreatedEvent event) {
        trace.add(System.currentTimeMillis(), TimelineEntryType.RULE_ACTIVATED,
            "RULE ACTIVATED \"" + event.getMatch().getRule().getName() + "\"");
    }

    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        trace.add(System.currentTimeMillis(), TimelineEntryType.RULE_CANCELLED,
            "RULE CANCELLED \"" + event.getMatch().getRule().getName() + "\"");
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        trace.add(System.currentTimeMillis(), TimelineEntryType.RULE_BEFORE_FIRE,
            "RULE ABOUT TO FIRE \"" + event.getMatch().getRule().getName() + "\"");
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        trace.add(System.currentTimeMillis(), TimelineEntryType.RULE_FIRED,
            "RULE FIRED \"" + event.getMatch().getRule().getName() + "\"");
    }

    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
        // no-op
    }

    @Override
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {
        // no-op
    }

    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        // no-op
    }

    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
        // no-op
    }

    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        // no-op
    }

    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
        // no-op
    }
}
