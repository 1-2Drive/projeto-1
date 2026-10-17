package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.decision.DecisionProposal;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.prolog.PrologBackend;
import com.one2drive.projeto1.prolog.PrologCycleResult;
import com.one2drive.projeto1.prolog.PrologEngineException;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance through meta-knowledge: the Prolog engine's dependency index (engine.pl,
 * rule_reads/2) re-matches after each firing only the rules that read a new fact type.
 * Every scenario of the library runs twice on Prolog, with the index and without it (every
 * rule re-matched after every firing, a naive recognise-act cycle). The decisions must be
 * identical in every cycle; the cost of the two runs is printed for comparison.
 */
class DependencyIndexBenchmarkTest {

    private static final int TICKS_PER_SCENARIO = 150;

    /**
     * The benchmark starts two SWI-Prolog processes per scenario. Under a busy machine (the
     * whole suite running) a process may miss the 2 s it has to answer its first request, so
     * starting one is retried; the cycles measured are not affected.
     */
    private static SimulationEngine startProlog(ScenarioDefinition scenario) {
        for (int attempt = 1; ; attempt++) {
            try {
                return BackendTestSupport.engine(scenario, ExpertBackendType.PROLOG);
            } catch (PrologEngineException e) {
                if (attempt == 3) {
                    throw e;
                }
            }
        }
    }

    private static PrologBackend prolog(SimulationEngine engine) {
        try {
            Field field = SimulationEngine.class.getDeclaredField("backend");
            field.setAccessible(true);
            return (PrologBackend) field.get(engine);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Everything the decision says, the proposals and their outcomes included. */
    private static String describe(DrivingDecision decision) {
        StringBuilder text = new StringBuilder()
            .append(decision.getSpeedAction()).append(' ')
            .append(String.format(Locale.ROOT, "%.3f", decision.getRecommendedSpeedKmh())).append(' ')
            .append(decision.getRiskLevel()).append(" p").append(decision.getPriority()).append(' ')
            .append(decision.getSpeedSourceRule()).append(" | ")
            .append(decision.getLaneAction()).append(" p").append(decision.getLanePriority()).append(' ')
            .append(decision.getLaneSourceRule());
        for (DecisionProposal proposal : decision.getProposals()) {
            text.append(" | ").append(proposal.getSourceRule()).append(' ').append(proposal.getAction())
                .append(' ').append(proposal.getOutcome());
        }
        return text.toString();
    }

    @Test
    @Timeout(900)
    @DisplayName("the dependency index saves rule evaluations and leaves every decision unchanged")
    void indexSavesWorkAndDecidesTheSame() {
        List<ScenarioDefinition> scenarios = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json").getScenarios();
        long[] rematches = new long[2];
        long[] firings = new long[2];
        double[] millis = new double[2];
        int cycles = 0;

        System.out.println("scenario                        cycles  rematches(index/none)  ms per cycle(index/none)");
        for (ScenarioDefinition scenario : scenarios) {
            try (SimulationEngine indexed = startProlog(scenario);
                 SimulationEngine naive = startProlog(scenario)) {
                prolog(naive).session().setDependencyIndex(false);
                indexed.start();
                naive.start();
                long[] r = new long[2];
                double[] ms = new double[2];
                for (int tick = 0; tick < TICKS_PER_SCENARIO; tick++) {
                    indexed.tick();
                    naive.tick();
                    assertEquals(describe(indexed.getCurrentDecision()), describe(naive.getCurrentDecision()),
                        scenario.getId() + " tick " + tick);
                    PrologCycleResult.EngineStats a = prolog(indexed).lastCycleResult().engineStats();
                    PrologCycleResult.EngineStats b = prolog(naive).lastCycleResult().engineStats();
                    assertEquals(a.firings(), b.firings(), scenario.getId() + " tick " + tick + ": same rules fired");
                    r[0] += a.rematches();
                    r[1] += b.rematches();
                    ms[0] += a.ruleMillis();
                    ms[1] += b.ruleMillis();
                    firings[0] += a.firings();
                    firings[1] += b.firings();
                }
                cycles += TICKS_PER_SCENARIO;
                rematches[0] += r[0];
                rematches[1] += r[1];
                millis[0] += ms[0];
                millis[1] += ms[1];
                System.out.printf(Locale.ROOT, "%-30s %7d  %9d / %-9d       %6.2f / %-6.2f%n", scenario.getId(),
                    TICKS_PER_SCENARIO, r[0], r[1], ms[0] / TICKS_PER_SCENARIO, ms[1] / TICKS_PER_SCENARIO);
            }
        }
        System.out.printf(Locale.ROOT, "TOTAL %d cycles, %d firings: rematches %d / %d (%.1f%% fewer), "
                + "%.2f / %.2f ms per cycle (%.1f%% less)%n",
            cycles, firings[0], rematches[0], rematches[1], 100.0 * (1 - (double) rematches[0] / rematches[1]),
            millis[0] / cycles, millis[1] / cycles, 100.0 * (1 - millis[0] / millis[1]));
        assertTrue(rematches[0] < rematches[1], "the index must save rule evaluations");
    }
}
