package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.rule.Match;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meta-knowledge (control.drl): in every cycle the control rule fires first, then every
 * perception rule, then the decision rules, then the fallback baseline, whatever the
 * salience of each rule.
 */
class AgendaPhasesTest {

    /** The public Rule API does not expose the agenda-group; the engine's rule class does. */
    private static String groupOf(Match match) {
        try {
            return (String) match.getRule().getClass().getMethod("getAgendaGroup").invoke(match.getRule());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("each cycle fires control, perception, decision and fallback in that order")
    void phasesFireInOrder() {
        try (DroolsTestHarness harness = new DroolsTestHarness(60.0)) {
            List<String> groups = new ArrayList<>();
            harness.session().getKieSession().addEventListener(new DefaultAgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    groups.add(groupOf(event.getMatch()));
                }
            });

            for (long t = 1_000; t <= 1_400; t += 200) {
                groups.clear();
                harness.cycle(t, 60.0,
                    new WeatherEvent(t, "env", WeatherCondition.RAIN, 0.7, 0.65),
                    new TrafficLightEvent(t, "camera", TrafficLightState.RED, 80, "light-1"),
                    new TTCEvent(t, "radar", 4.0, "front-1"));

                assertEquals("MAIN", groups.get(0), "the control rule fires first: " + groups);
                List<String> order = List.of("MAIN", "perception", "decision", "fallback");
                for (int i = 1; i < groups.size(); i++) {
                    assertTrue(order.indexOf(groups.get(i - 1)) <= order.indexOf(groups.get(i)),
                        "phases out of order at firing " + i + ": " + groups);
                }
                assertTrue(groups.contains("perception") && groups.contains("decision"),
                    "perception and decision both fire: " + groups);
            }
        }
    }
}
