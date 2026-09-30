package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.facts.AccelerationRequestState;
import com.one2drive.projeto1.drools.facts.BarrierStateFact;
import com.one2drive.projeto1.drools.facts.CurrentSpeedLimit;
import com.one2drive.projeto1.drools.facts.FrontVehicleState;
import com.one2drive.projeto1.drools.facts.LaneChangeRequestState;
import com.one2drive.projeto1.drools.facts.LaneOccupancyState;
import com.one2drive.projeto1.drools.facts.PedestrianStateFact;
import com.one2drive.projeto1.drools.facts.PotholeStateFact;
import com.one2drive.projeto1.drools.facts.RoadworksStateFact;
import com.one2drive.projeto1.drools.facts.RoundaboutStateFact;
import com.one2drive.projeto1.drools.facts.StopSignState;
import com.one2drive.projeto1.drools.facts.TrafficLightStateFact;
import com.one2drive.projeto1.drools.facts.WeatherStateFact;
import com.one2drive.projeto1.drools.facts.YieldSignState;
import com.one2drive.projeto1.drools.listeners.RuleRuntimeTraceListener;
import com.one2drive.projeto1.drools.listeners.WorkingMemoryTraceListener;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.ViewChangedEventListener;

import java.util.function.Consumer;

public class KnowledgeSession implements AutoCloseable {
    private static final KieContainer KIE_CONTAINER = createKieContainer();

    private final KieSession kieSession;
    private final EventRouter router;
    private final WorkingMemoryInspector inspector;
    private final ExplanationTrace trace;
    private final DecisionContext decisionContext;
    private final DrivingDecision drivingDecision;
    private final LiveQuery liveQuery;

    private KnowledgeSession(KieSession kieSession,
                             EventRouter router,
                             WorkingMemoryInspector inspector,
                             ExplanationTrace trace,
                             DecisionContext decisionContext,
                             DrivingDecision drivingDecision,
                             LiveQuery liveQuery) {
        this.kieSession = kieSession;
        this.router = router;
        this.inspector = inspector;
        this.trace = trace;
        this.decisionContext = decisionContext;
        this.drivingDecision = drivingDecision;
        this.liveQuery = liveQuery;
    }

    public static KnowledgeSession createRealtime(ExplanationTrace trace, Consumer<DrivingDecision> decisionConsumer) {
        return create(trace, decisionConsumer, false, true);
    }

    public static KnowledgeSession createPseudoClock(ExplanationTrace trace, Consumer<DrivingDecision> decisionConsumer) {
        return create(trace, decisionConsumer, true, true);
    }

    public static KnowledgeSession createPseudoClockForTests(ExplanationTrace trace) {
        return create(trace, decision -> {
        }, true, false);
    }

    private static KnowledgeSession create(ExplanationTrace trace,
                                           Consumer<DrivingDecision> decisionConsumer,
                                           boolean pseudoClock,
                                           boolean enableLiveQuery) {
        KieServices ks = KieServices.Factory.get();

        var configuration = ks.newKieSessionConfiguration();
        if (pseudoClock) {
            configuration.setOption(ClockTypeOption.PSEUDO);
        } else {
            configuration.setOption(ClockTypeOption.REALTIME);
        }

        KieSession kieSession = KIE_CONTAINER.newKieSession(configuration);
        if (kieSession == null) {
            throw new IllegalStateException("Unable to create KieSession from classpath container");
        }
        kieSession.addEventListener(new RuleRuntimeTraceListener(trace));
        kieSession.addEventListener(new WorkingMemoryTraceListener(trace));

        DecisionContext context = new DecisionContext();
        DrivingDecision decision = new DrivingDecision();
        DroolsSupport support = new DroolsSupport();

        kieSession.setGlobal("droolsSupport", support);

        kieSession.insert(context);
        kieSession.insert(decision);
        kieSession.insert(new CurrentSpeedLimit(120, "default", 0L));
        kieSession.insert(new WeatherStateFact(com.one2drive.projeto1.domain.WeatherCondition.CLEAR, 1.0, 1.0, 0L));
        kieSession.insert(new LaneOccupancyState(com.one2drive.projeto1.domain.Lane.LEFT, false, Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new LaneOccupancyState(com.one2drive.projeto1.domain.Lane.RIGHT, false, Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new FrontVehicleState("none", Double.POSITIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new PedestrianStateFact("none", Double.POSITIVE_INFINITY, false, 0L));
        kieSession.insert(new PotholeStateFact("none", Double.POSITIVE_INFINITY, com.one2drive.projeto1.domain.HazardSeverity.LOW, 0L));
        kieSession.insert(new RoadworksStateFact("none", Double.POSITIVE_INFINITY, com.one2drive.projeto1.domain.HazardSeverity.LOW, 0L));
        kieSession.insert(new BarrierStateFact("none", Double.POSITIVE_INFINITY, true, 0L));
        kieSession.insert(new RoundaboutStateFact("none", Double.POSITIVE_INFINITY, 30, false, 0L));
        kieSession.insert(new StopSignState("none", Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new YieldSignState("none", Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new TrafficLightStateFact("none", com.one2drive.projeto1.domain.TrafficLightState.GREEN, Double.POSITIVE_INFINITY, 0L));
        kieSession.insert(new AccelerationRequestState(0.0, 0L));
        kieSession.insert(new LaneChangeRequestState(false, com.one2drive.projeto1.domain.LaneChangeDirection.LEFT, 0L));

        LiveQuery liveQuery = null;
        if (enableLiveQuery) {
            ViewChangedEventListener listener = new DecisionLiveQueryListener(decisionConsumer);
            liveQuery = kieSession.openLiveQuery("Current Driving Decision", new Object[]{}, listener);
        }

        return new KnowledgeSession(
            kieSession,
            new EventRouter(kieSession),
            new WorkingMemoryInspector(kieSession),
            trace,
            context,
            decision,
            liveQuery
        );
    }

    private static KieContainer createKieContainer() {
        return KieServices.Factory.get().getKieClasspathContainer();
    }

    public KieSession getKieSession() {
        return kieSession;
    }

    public EventRouter getRouter() {
        return router;
    }

    public WorkingMemoryInspector getInspector() {
        return inspector;
    }

    public ExplanationTrace getTrace() {
        return trace;
    }

    public DecisionContext getDecisionContext() {
        return decisionContext;
    }

    public DrivingDecision getDrivingDecision() {
        return drivingDecision;
    }

    @Override
    public void close() {
        if (liveQuery != null) {
            liveQuery.close();
        }
        kieSession.dispose();
    }
}
