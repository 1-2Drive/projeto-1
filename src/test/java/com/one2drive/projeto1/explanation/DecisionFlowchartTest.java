package com.one2drive.projeto1.explanation;

import com.one2drive.projeto1.domain.DecisionChannel;
import com.one2drive.projeto1.explanation.DecisionFlowchart.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flowchart map must follow the knowledge bases: every rule that proposes, in Drools and in
 * Prolog, answers one question of the flowchart in its own channel, and every rule the map
 * names exists. A rule added to the rules without a place in the flowchart fails here.
 */
class DecisionFlowchartTest {
    private static final Pattern DRL_RULE = Pattern.compile("(?m)^rule \"([^\"]+)\"(.*?)^end", Pattern.DOTALL);
    private static final Pattern PROLOG_RULE = Pattern.compile("(?m)^rule\\('((?:[^'\\\\]|\\\\.)*)',");
    private static final Pattern LATERAL_ACTION =
        Pattern.compile("DrivingAction\\.(KEEP_LANE|CHANGE_LANE_LEFT|CHANGE_LANE_RIGHT|RETURN_RIGHT)");
    private static final Pattern PROLOG_LATERAL = Pattern.compile("proposal\\((keep_lane|change_lane_left|change_lane_right|return_right),");

    @Test
    void everyProposingDroolsRuleAnswersAQuestionOfItsChannel() throws IOException {
        Map<String, String> rules = droolsRules();
        List<String> missing = new ArrayList<>();
        rules.forEach((name, body) -> {
            if (!body.contains("proposeDecision(")) {
                return;
            }
            DecisionChannel channel = LATERAL_ACTION.matcher(body).find() ? DecisionChannel.LATERAL : DecisionChannel.LONGITUDINAL;
            Optional<Node> node = DecisionFlowchart.nodeFor(name);
            if (node.isEmpty() || node.get().channel() != channel) {
                missing.add(name + " (" + channel + ")");
            }
        });
        assertTrue(missing.isEmpty(), "rules without a flowchart question: " + missing);
    }

    @Test
    void everyProposingPrologRuleAnswersAQuestionOfItsChannel() throws IOException {
        String source = Files.readString(Path.of("src/main/prolog/driving_rules.pl"), StandardCharsets.UTF_8);
        Matcher matcher = PROLOG_RULE.matcher(source);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1).replace("\\'", "'"));
            starts.add(matcher.start());
        }
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
            String body = source.substring(starts.get(i), end);
            if (!body.contains("proposal(")) {
                continue;
            }
            DecisionChannel channel = PROLOG_LATERAL.matcher(body).find() ? DecisionChannel.LATERAL : DecisionChannel.LONGITUDINAL;
            Optional<Node> node = DecisionFlowchart.nodeFor(names.get(i));
            if (node.isEmpty() || node.get().channel() != channel) {
                missing.add(names.get(i) + " (" + channel + ")");
            }
        }
        assertTrue(names.size() > 50, "the Prolog rules were read");
        assertTrue(missing.isEmpty(), "Prolog rules without a flowchart question: " + missing);
    }

    @Test
    void everyRuleTheFlowchartNamesExists() throws IOException {
        Set<String> drools = droolsRules().keySet();
        String prolog = Files.readString(Path.of("src/main/prolog/driving_rules.pl"), StandardCharsets.UTF_8);
        List<String> unknown = new ArrayList<>();
        Stream.of(DecisionChannel.values()).flatMap(c -> DecisionFlowchart.nodes(c).stream()).forEach(node -> Stream.of(
                node.rules(), node.supportingRules(), node.hazardRules()).flatMap(List::stream).forEach(rule -> {
            if (!drools.contains(rule) && !prolog.contains("rule('" + rule)) {
                unknown.add(node.id() + ": " + rule);
            }
        }));
        assertTrue(unknown.isEmpty(), "rules named by the flowchart but absent from the knowledge bases: " + unknown);
    }

    @Test
    void prologVariantsMapToTheirDroolsRule() {
        assertEquals("Brake for pedestrian proximity", DecisionFlowchart.baseRuleName("Brake for pedestrian proximity (brake)"));
        assertEquals("V08", DecisionFlowchart.nodeFor("Brake for pedestrian proximity (hold speed)").orElseThrow().id());
        assertEquals("VS", DecisionFlowchart.nodeFor("Hold at red traffic light (emergency)").orElseThrow().id());
        assertEquals("Fuzzy: poor visibility or poor grip -> very slow",
            DecisionFlowchart.baseRuleName("Fuzzy: poor visibility or poor grip -> very slow"));
    }

    @Test
    void eachRuleAnswersOneQuestionOnly() {
        Map<String, String> seen = new LinkedHashMap<>();
        List<String> twice = new ArrayList<>();
        Stream.of(DecisionChannel.values()).flatMap(c -> DecisionFlowchart.nodes(c).stream()).forEach(node ->
            node.rules().forEach(rule -> {
                String previous = seen.put(rule, node.id());
                if (previous != null) {
                    twice.add(rule + " in " + previous + " and " + node.id());
                }
            }));
        assertTrue(twice.isEmpty(), twice.toString());
    }

    private static Map<String, String> droolsRules() throws IOException {
        Map<String, String> rules = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/rules"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".drl")).sorted().toList()) {
                Matcher matcher = DRL_RULE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    rules.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        assertTrue(rules.size() > 50, "the Drools rules were read");
        return rules;
    }
}
