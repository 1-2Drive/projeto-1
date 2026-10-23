package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a sensor reading is a meaningful transition or just the next
 * numerical sample of an unchanged situation.
 *
 * <p>Continuous samples still reach Drools and Working Memory; this only keeps the
 * inference timeline readable by reporting first detection, loss of detection, and
 * threshold crossings (notably TTC warning and critical bands).
 */
public class SensorSignificance {

    /** TTC bands used to report a transition when the time-to-collision changes severity. */
    public enum TtcBand {
        NONE,
        SAFE,
        WARNING,
        CRITICAL;

        static TtcBand of(double ttcSeconds) {
            if (!Double.isFinite(ttcSeconds)) {
                return NONE;
            }
            if (ttcSeconds < 2.0) {
                return CRITICAL;
            }
            if (ttcSeconds < 5.0) {
                return WARNING;
            }
            return SAFE;
        }
    }

    private final Map<String, Boolean> detected = new HashMap<>();
    private final Map<String, Boolean> occupancy = new HashMap<>();
    private TtcBand ttcBand = TtcBand.NONE;
    private Double lastSpeedLimit = null;

    public void reset() {
        detected.clear();
        occupancy.clear();
        ttcBand = TtcBand.NONE;
        lastSpeedLimit = null;
    }

    /**
     * @return a human-readable transition when the reading is significant, otherwise null
     */
    public String classify(Object event) {
        if (event instanceof PotholeDetectedEvent e) {
            if ("none".equals(e.getPotholeId()) || Double.isInfinite(e.getDistanceMeters()) || e.getDistanceMeters() <= 0) {
                return null;
            }
            return transition("pothole:" + e.getPotholeId(), true, describePothole(e));
        }
        if (event instanceof ObstacleDetectedEvent e) {
            if ("none".equals(e.getObstacleId()) || Double.isInfinite(e.getDistanceMeters()) || e.getDistanceMeters() <= 0) {
                return null;
            }
            return transition("obstacle:" + e.getObstacleId(), true, describeObstacle(e));
        }
        if (event instanceof PedestrianDetectedEvent e) {
            if ("none".equals(e.getPedestrianId()) || Double.isInfinite(e.getDistanceMeters()) || e.getDistanceMeters() <= 0) {
                return null;
            }
            return transition("pedestrian:" + e.getPedestrianId(), true,
                "Pedestrian detected " + round(e.getDistanceMeters()) + "m ahead"
                    + (e.isInPath() ? " in path" : " outside path"));
        }
        if (event instanceof SpeedLimitDetectedEvent e) {
            if (lastSpeedLimit != null && lastSpeedLimit == e.getLimitKmh()) {
                return null;
            }
            lastSpeedLimit = (double) e.getLimitKmh();
            return "Speed limit detected " + e.getLimitKmh() + " km/h";
        }
        if (event instanceof LaneOccupancyEvent e) {
            String key = "occupancy:" + e.getLane();
            boolean was = Boolean.TRUE.equals(occupancy.get(key));
            occupancy.put(key, e.isOccupied());
            if (was == e.isOccupied()) {
                return null;
            }
            return e.isOccupied()
                ? "Lane " + e.getLane() + " occupied"
                : "Lane " + e.getLane() + " clear";
        }
        if (event instanceof TTCEvent e) {
            TtcBand band = TtcBand.of(e.getTtcSeconds());
            if (band == ttcBand) {
                return null;
            }
            ttcBand = band;
            return switch (band) {
                case CRITICAL -> "TTC critical at " + round(e.getTtcSeconds()) + "s";
                case WARNING -> "TTC entered warning range at " + round(e.getTtcSeconds()) + "s";
                case SAFE -> "TTC recovered to " + round(e.getTtcSeconds()) + "s";
                case NONE -> "TTC target lost";
            };
        }
        // Continuous numeric samples: not significant on their own.
        if (event instanceof FrontDistanceEvent || event instanceof RelativeSpeedEvent) {
            return null;
        }
        return null;
    }

    private String transition(String key, boolean nowDetected, String description) {
        boolean wasDetected = Boolean.TRUE.equals(detected.get(key));
        if (wasDetected == nowDetected) {
            return null;
        }
        detected.put(key, nowDetected);
        return description;
    }

    /** Reports that a previously detected object is no longer perceived. */
    public String classifyLoss(String objectId, String label) {
        String key = label.toLowerCase() + ":" + objectId;
        if (!Boolean.TRUE.equals(detected.get(key))) {
            return null;
        }
        detected.remove(key);
        return label + " no longer detected";
    }

    private static String describePothole(PotholeDetectedEvent e) {
        return "Pothole detected " + round(e.getDistanceMeters()) + "m ahead in " + e.getLane() + " lane";
    }

    private static String describeObstacle(ObstacleDetectedEvent e) {
        return "Obstacle detected " + round(e.getDistanceMeters()) + "m ahead in " + e.getLane() + " lane";
    }

    private static String round(double value) {
        return Double.isFinite(value) ? String.format("%.1f", value) : "none";
    }
}
