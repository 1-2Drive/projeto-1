package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.decision.DecisionContext;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.drools.facts.BarrierStateFact;
import com.one2drive.projeto1.drools.facts.CurrentSpeedLimit;
import com.one2drive.projeto1.drools.facts.CruiseTargetFact;
import com.one2drive.projeto1.drools.facts.FrontVehicleState;
import com.one2drive.projeto1.drools.facts.LaneOccupancyState;
import com.one2drive.projeto1.drools.facts.PedestrianStateFact;
import com.one2drive.projeto1.drools.facts.PotholeStateFact;
import com.one2drive.projeto1.drools.facts.StopSignState;
import com.one2drive.projeto1.drools.facts.TrafficLightStateFact;
import com.one2drive.projeto1.drools.facts.VehicleStateFact;
import com.one2drive.projeto1.drools.facts.WeatherStateFact;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.facts.ProcessedEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.RoadGripEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.VehicleSpeedEvent;
import com.one2drive.projeto1.drools.events.VisibilityEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Renders working-memory facts and sensor events as one short, readable line for the inference
 * timeline. This only formats what Drools already inserted; it never derives
 * or changes any driving meaning.
 */
public final class FactDescription {

    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private FactDescription() {
    }

    public static String describe(Object fact) {
        if (fact == null) {
            return "null";
        }
        if (fact instanceof VehicleStateFact value) {
            return "Vehicle state: %s km/h at %s m, lane %s".formatted(
                decimal(value.getSpeedKmh(), 1), decimal(value.getPositionMeters(), 1), value.getLane());
        }
        if (fact instanceof CurrentSpeedLimit value) {
            return "Speed limit: %d km/h in force (source %s)".formatted(value.getLimitKmh(), value.getSourceId());
        }
        if (fact instanceof com.one2drive.projeto1.drools.facts.PendingSpeedLimit value) {
            return com.one2drive.projeto1.drools.facts.PendingSpeedLimit.NONE.equals(value.getSignId())
                ? "Next speed limit: none ahead"
                : "Next speed limit: %d km/h pending until sign %s".formatted(value.getLimitKmh(), id(value.getSignId()));
        }
        if (fact instanceof StopSignState value) {
            return "Stop sign %s: stop line %s m".formatted(id(value.getSignId()), decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof CruiseTargetFact value) {
            return "Cruise target: %s km/h from the scenario".formatted(decimal(value.getTargetKmh(), 1));
        }
        if (fact instanceof WeatherStateFact value) {
            return "Weather: %s, visibility %s, grip %s".formatted(
                value.getCondition(), decimal(value.getVisibilityFactor(), 2), decimal(value.getGripFactor(), 2));
        }
        if (fact instanceof FrontVehicleState value) {
            return "Front vehicle %s: %s m, relative speed %s m/s, %s".formatted(
                id(value.getTargetId()), decimal(value.getDistanceMeters(), 1),
                decimal(value.getRelativeSpeedMps(), 2), ttc(value.getTtcSeconds()));
        }
        if (fact instanceof PedestrianStateFact value) {
            return "Pedestrian %s: %s m, in path %s".formatted(
                id(value.getPedestrianId()), decimal(value.getDistanceMeters(), 1), value.isInPath());
        }
        if (fact instanceof TrafficLightStateFact value) {
            return "Traffic light %s: %s at %s m".formatted(
                id(value.getLightId()), value.getState(), decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof PotholeStateFact value) {
            return "Pothole %s: %s m, severity %s".formatted(
                id(value.getPotholeId()), decimal(value.getDistanceMeters(), 1), value.getSeverity());
        }
        if (fact instanceof BarrierStateFact value) {
            return "Barrier %s: %s, %s m".formatted(
                id(value.getBarrierId()), value.isOpen() ? "open" : "closed", decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof LaneOccupancyState value) {
            return "Lane %s: %s, nearest %s".formatted(
                value.getLane(), value.isOccupied() ? "occupied" : "clear", distance(value.getNearestDistanceMeters()));
        }
        if (fact instanceof DrivingDecision value) {
            return "Decision %s (%s risk) → %s km/h, rule %s".formatted(
                value.getAction(), value.getRiskLevel(),
                decimal(value.getRecommendedSpeedKmh(), 1), value.getSourceRule());
        }
        if (fact instanceof LaneOccupancyEvent value) {
            return "Lane %s: %s, nearest %s".formatted(
                value.getLane(), value.isOccupied() ? "occupied" : "clear", distance(value.getNearestDistanceMeters()));
        }
        if (fact instanceof SpeedLimitDetectedEvent value) {
            return "Camera read speed limit %d km/h (sign %s) at %s m".formatted(
                value.getLimitKmh(), id(value.getSignId()), decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof StopSignEvent value) {
            return "Camera read stop sign %s at %s m".formatted(id(value.getSignId()), decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof TrafficLightEvent value) {
            return "Camera read traffic light %s: %s at %s m".formatted(
                id(value.getLightId()), value.getState(), decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof PotholeDetectedEvent value) {
            return "Camera read pothole %s: %s m, severity %s".formatted(
                id(value.getPotholeId()), decimal(value.getDistanceMeters(), 1), value.getSeverity());
        }
        if (fact instanceof ObstacleDetectedEvent value) {
            return "Camera read obstacle %s: %s m, severity %s".formatted(
                id(value.getObstacleId()), decimal(value.getDistanceMeters(), 1), value.getSeverity());
        }
        if (fact instanceof PedestrianDetectedEvent value) {
            return "Camera read pedestrian %s: %s m, in path %s".formatted(
                id(value.getPedestrianId()), decimal(value.getDistanceMeters(), 1), value.isInPath());
        }
        if (fact instanceof BarrierDetectedEvent value) {
            return "Camera read barrier %s: %s at %s m".formatted(
                id(value.getBarrierId()), value.isOpen() ? "open" : "closed", decimal(value.getDistanceMeters(), 1));
        }
        if (fact instanceof FrontDistanceEvent value) {
            return "Radar measured %s m to %s".formatted(
                decimal(value.getDistanceMeters(), 1), id(value.getTargetId()));
        }
        if (fact instanceof RelativeSpeedEvent value) {
            return "Radar measured relative speed %s m/s to %s".formatted(
                decimal(value.getRelativeSpeedMps(), 2), id(value.getTargetId()));
        }
        if (fact instanceof TTCEvent value) {
            return "Radar: %s to %s".formatted(ttc(value.getTtcSeconds()), id(value.getTargetId()));
        }
        if (fact instanceof VehicleSpeedEvent value) {
            return "Ego speed %s km/h".formatted(decimal(value.getSpeedKmh(), 1));
        }
        if (fact instanceof ReducedVisibilityEvent value) {
            return "Environment: visibility reduced to %s".formatted(decimal(value.getVisibilityFactor(), 2));
        }
        if (fact instanceof ReducedGripEvent value) {
            return "Environment: road grip reduced to %s".formatted(decimal(value.getGripFactor(), 2));
        }
        if (fact instanceof WeatherEvent value) {
            return "Environment: %s, visibility %s, grip %s".formatted(
                value.getCondition(), decimal(value.getVisibilityFactor(), 2), decimal(value.getGripFactor(), 2));
        }
        if (fact instanceof VisibilityEvent value) {
            return "Environment: visibility %s".formatted(decimal(value.getVisibilityFactor(), 2));
        }
        if (fact instanceof RoadGripEvent value) {
            return "Environment: road grip %s".formatted(decimal(value.getGripFactor(), 2));
        }
        if (fact instanceof ProcessedEvent value) {
            return "Processed: " + describe(value.getEvent());
        }
        if (fact instanceof DecisionContext value) {
            return "Decision context evaluated, priority %d".formatted(value.getPriority());
        }
        return generic(fact);
    }

    /** Fallback for facts without a dedicated description: keeps the key=value tail readable. */
    private static String generic(Object fact) {
        String text = String.valueOf(fact);
        int open = text.indexOf('{');
        if (open <= 0 || !text.endsWith("}")) {
            return text;
        }
        String type = text.substring(0, open);
        String tail = text.substring(open + 1, text.length() - 1);
        if (tail.isEmpty()) {
            return type;
        }
        return type + " — " + tail;
    }

    private static String id(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw)) {
            return "none";
        }
        // Generated UUIDs are noise in a one-line description; keep a short, stable suffix.
        if (UUID_PATTERN.matcher(raw).matches()) {
            return "#" + raw.substring(raw.length() - 4);
        }
        return raw;
    }

    private static String ttc(double seconds) {
        return Double.isFinite(seconds) ? "TTC " + decimal(seconds, 2) + " s" : "no closing target";
    }

    private static String distance(double value) {
        return Double.isFinite(value) ? decimal(value, 1) + " m" : "none";
    }

    private static String decimal(double value, int scale) {
        if (!Double.isFinite(value)) {
            return "none";
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
