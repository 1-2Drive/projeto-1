package com.one2drive.projeto1.prolog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every Drools driving rule has a Prolog rule with the same name, and the other way round,
 * so a rule added or renamed in one engine and forgotten in the other fails here. Prolog ids
 * that split a Drools rule by outcome add a suffix in parentheses, which is ignored. The
 * rules of {@code 01-state-updates.drl} are not compared: Prolog folds events in
 * {@code driving_state.pl} instead of in rules. Reads the sources, so it needs no swipl.
 */
class RuleNameParityTest {

    private static final String STATE_UPDATES = "01-state-updates.drl";

    /** Drools rules with no Prolog rule of the same name, and why. */
    private static final Map<String, String> DROOLS_ONLY = Map.of(
        "Control: perceive, then decide, then fall back",
        "agenda-group control; Prolog orders rules by precedence in engine.pl",
        "Forget the stop when the stop sign is re-armed",
        "deletes a fact; Prolog never retracts in a cycle and re-arms the stop in driving_state.pl");

    /** Prolog rules with no Drools rule of the same name, and why. */
    private static final Map<String, String> PROLOG_ONLY = Map.of(
        "Pedestrian conflicts with trajectory",
        "intermediate conclusion for a disjunction inside the Drools pedestrian rules");

    private static final Pattern DRL_RULE = Pattern.compile("^\\s*rule\\s+\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern PROLOG_RULE = Pattern.compile("^rule\\('((?:[^'\\\\]|\\\\.|'')*)'", Pattern.MULTILINE);
    private static final Pattern OUTCOME_SUFFIX = Pattern.compile(" \\([^)]*\\)$");

    @Test
    @DisplayName("every Drools driving rule has a Prolog rule of the same name, and vice versa")
    void ruleNamesMatch() throws IOException, URISyntaxException {
        Set<String> drools = droolsRuleNames();
        Set<String> prolog = prologRuleBaseNames();
        assertFalse(drools.isEmpty(), "no Drools rules read");
        assertFalse(prolog.isEmpty(), "no Prolog rules read");

        Set<String> missingInProlog = new TreeSet<>(drools);
        missingInProlog.removeAll(prolog);
        missingInProlog.removeAll(DROOLS_ONLY.keySet());
        Set<String> missingInDrools = new TreeSet<>(prolog);
        missingInDrools.removeAll(drools);
        missingInDrools.removeAll(PROLOG_ONLY.keySet());

        assertTrue(missingInProlog.isEmpty(),
            "Drools rules with no Prolog rule (replicate them in driving_rules.pl): " + missingInProlog);
        assertTrue(missingInDrools.isEmpty(),
            "Prolog rules with no Drools rule (rename them after the Drools rule): " + missingInDrools);
    }

    @Test
    @DisplayName("the listed exceptions still exist and are still one-sided")
    void exceptionsAreCurrent() throws IOException, URISyntaxException {
        Set<String> drools = droolsRuleNames();
        Set<String> prolog = prologRuleBaseNames();
        for (String name : DROOLS_ONLY.keySet()) {
            assertTrue(drools.contains(name) && !prolog.contains(name),
                "stale Drools-only exception: " + name);
        }
        for (String name : PROLOG_ONLY.keySet()) {
            assertTrue(prolog.contains(name) && !drools.contains(name),
                "stale Prolog-only exception: " + name);
        }
    }

    private static Set<String> droolsRuleNames() throws IOException, URISyntaxException {
        URL rules = RuleNameParityTest.class.getClassLoader().getResource("rules");
        assertTrue(rules != null, "rules/ not on the classpath");
        List<Path> files;
        try (Stream<Path> listing = Files.list(Path.of(rules.toURI()))) {
            files = listing
                .filter(file -> file.getFileName().toString().endsWith(".drl"))
                .filter(file -> !file.getFileName().toString().equals(STATE_UPDATES))
                .toList();
        }
        Set<String> names = new TreeSet<>();
        for (Path file : files) {
            names.addAll(matches(DRL_RULE, Files.readString(file)));
        }
        return names;
    }

    private static Set<String> prologRuleBaseNames() throws IOException {
        String source;
        try (InputStream in = RuleNameParityTest.class.getClassLoader()
                .getResourceAsStream("prolog/driving_rules.pl")) {
            assertTrue(in != null, "prolog/driving_rules.pl not on the classpath");
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> names = new TreeSet<>();
        for (String id : matches(PROLOG_RULE, source)) {
            names.add(OUTCOME_SUFFIX.matcher(id.replace("''", "'")).replaceFirst(""));
        }
        return names;
    }

    private static List<String> matches(Pattern pattern, String source) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
