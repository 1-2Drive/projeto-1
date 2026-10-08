package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Meaning of each number stored in the {@link RoadMap}. 0 is always a free cell; every
 * other code names one kind of fixed road element together with its parameters, so a
 * 50 km/h limit and a 90 km/h limit are different codes.
 */
public final class MapLegend {
    public static final int FREE = 0;

    private static final int[] DEFAULT_SPEED_LIMITS = {30, 50, 70, 90, 100, 120};

    /** One code of the legend. The template carries type and parameters, never a position. */
    public record Entry(int code, String label, ScenarioRoadElementDefinition template) {

        /** Short parameter token for the UI: the limit, the light colour, the severity or the barrier state. */
        public String detail() {
            return switch (template.getType()) {
                case SPEED_LIMIT -> String.valueOf(template.getSpeedLimitKmh());
                case TRAFFIC_LIGHT -> template.getTrafficLightState().name();
                case POTHOLE, STATIC_OBSTACLE -> template.getSeverity().name();
                case BARRIER -> Boolean.TRUE.equals(template.getBarrierOpen()) ? "OPEN" : "CLOSED";
                case STOP_SIGN -> "";
                case SPEED_RADAR -> String.valueOf((int) template.getSpeedLimitKmh());
            };
        }
    }

    private final TreeMap<Integer, Entry> entries = new TreeMap<>();

    public static MapLegend defaults() {
        MapLegend legend = new MapLegend();
        legend.put(1, hazard(ScenarioRoadElementType.STATIC_OBSTACLE, HazardSeverity.HIGH));
        legend.put(2, hazard(ScenarioRoadElementType.POTHOLE, HazardSeverity.HIGH));
        legend.put(3, barrier(false));
        legend.put(4, element(ScenarioRoadElementType.STOP_SIGN));
        legend.put(6, trafficLight(TrafficLightState.RED));
        legend.put(7, trafficLight(TrafficLightState.GREEN));
        int code = 8;
        for (int limit : DEFAULT_SPEED_LIMITS) {
            legend.put(code++, speedLimit(limit));
        }
        return legend;
    }

    /** Copy of this legend with scenario specific codes added or replaced. */
    public MapLegend withOverrides(Map<Integer, ScenarioRoadElementDefinition> overrides) {
        MapLegend copy = new MapLegend();
        entries.values().forEach(entry -> copy.put(entry.code(), entry.template()));
        if (overrides != null) {
            overrides.forEach((code, template) -> {
                if (code == null || code <= FREE) {
                    throw new IllegalArgumentException("Road map codes must be positive, got " + code);
                }
                if (template == null || template.getType() == null) {
                    throw new IllegalArgumentException("Road map code " + code + " needs a type");
                }
                copy.put(code, template);
            });
        }
        return copy;
    }

    public Optional<Entry> entry(int code) {
        return Optional.ofNullable(entries.get(code));
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    /**
     * Code for an element definition. When no code has the same type and parameters
     * (for example a 40 km/h limit placed from the toolbox) a new code is added.
     */
    public int codeFor(ScenarioRoadElementDefinition definition) {
        ScenarioRoadElementDefinition wanted = normalize(definition);
        for (Entry entry : entries.values()) {
            if (sameElement(entry.template(), wanted)) {
                return entry.code();
            }
        }
        int code = entries.isEmpty() ? 1 : entries.lastKey() + 1;
        put(code, wanted);
        return code;
    }

    private void put(int code, ScenarioRoadElementDefinition template) {
        ScenarioRoadElementDefinition normalized = normalize(template);
        entries.put(code, new Entry(code, describe(normalized), normalized));
    }

    /** Keeps only the parameters that matter for the type, with the same defaults ScenarioFactory applies. */
    private static ScenarioRoadElementDefinition normalize(ScenarioRoadElementDefinition source) {
        ScenarioRoadElementDefinition template = element(source.getType());
        switch (source.getType()) {
            case SPEED_LIMIT -> {
                if (source.getSpeedLimitKmh() == null) {
                    throw new IllegalArgumentException("A speed limit code needs speedLimitKmh");
                }
                template.setSpeedLimitKmh(source.getSpeedLimitKmh());
            }
            case TRAFFIC_LIGHT -> template.setTrafficLightState(
                source.getTrafficLightState() == null ? TrafficLightState.RED : source.getTrafficLightState());
            case POTHOLE -> template.setSeverity(source.getSeverity() == null ? HazardSeverity.MEDIUM : source.getSeverity());
            case STATIC_OBSTACLE -> template.setSeverity(source.getSeverity() == null ? HazardSeverity.HIGH : source.getSeverity());
            case BARRIER -> template.setBarrierOpen(source.getBarrierOpen() == null || source.getBarrierOpen());
            case STOP_SIGN -> {
            }
            case SPEED_RADAR -> {
                template.setSpeedLimitKmh(source.getSpeedLimitKmh() != null ? source.getSpeedLimitKmh() : 50);
            }
        }
        return template;
    }

    private static boolean sameElement(ScenarioRoadElementDefinition a, ScenarioRoadElementDefinition b) {
        return a.getType() == b.getType()
            && Objects.equals(a.getSpeedLimitKmh(), b.getSpeedLimitKmh())
            && a.getTrafficLightState() == b.getTrafficLightState()
            && a.getSeverity() == b.getSeverity()
            && Objects.equals(a.getBarrierOpen(), b.getBarrierOpen());
    }

    private static String describe(ScenarioRoadElementDefinition template) {
        return switch (template.getType()) {
            case STATIC_OBSTACLE -> "Obstacle" + severitySuffix(template.getSeverity());
            case POTHOLE -> "Pothole" + severitySuffix(template.getSeverity());
            case BARRIER -> Boolean.TRUE.equals(template.getBarrierOpen()) ? "Open barrier" : "Closed barrier";
            case STOP_SIGN -> "Stop sign";
            case TRAFFIC_LIGHT -> switch (template.getTrafficLightState()) {
                case RED -> "Red light";
                case YELLOW -> "Yellow light";
                case GREEN -> "Green light";
            };
            case SPEED_LIMIT -> "Limit " + template.getSpeedLimitKmh() + " km/h";
            case SPEED_RADAR -> "Speed radar " + (int) template.getSpeedLimitKmh() + " km/h";
        };
    }

    private static String severitySuffix(HazardSeverity severity) {
        return severity == HazardSeverity.HIGH ? "" : " (" + severity.name().toLowerCase() + ")";
    }

    private static ScenarioRoadElementDefinition element(ScenarioRoadElementType type) {
        ScenarioRoadElementDefinition template = new ScenarioRoadElementDefinition();
        template.setType(type);
        return template;
    }

    private static ScenarioRoadElementDefinition hazard(ScenarioRoadElementType type, HazardSeverity severity) {
        ScenarioRoadElementDefinition template = element(type);
        template.setSeverity(severity);
        return template;
    }

    private static ScenarioRoadElementDefinition barrier(boolean open) {
        ScenarioRoadElementDefinition template = element(ScenarioRoadElementType.BARRIER);
        template.setBarrierOpen(open);
        return template;
    }

    private static ScenarioRoadElementDefinition trafficLight(TrafficLightState state) {
        ScenarioRoadElementDefinition template = element(ScenarioRoadElementType.TRAFFIC_LIGHT);
        template.setTrafficLightState(state);
        return template;
    }

    private static ScenarioRoadElementDefinition speedLimit(int limitKmh) {
        ScenarioRoadElementDefinition template = element(ScenarioRoadElementType.SPEED_LIMIT);
        template.setSpeedLimitKmh(limitKmh);
        return template;
    }

    private static ScenarioRoadElementDefinition speedRadar(int limitKmh) {
        ScenarioRoadElementDefinition template = element(ScenarioRoadElementType.SPEED_RADAR);
        template.setSpeedLimitKmh(limitKmh);
        return template;
    }
}
