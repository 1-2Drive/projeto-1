package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.drools.events.BaseEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.QueryResults;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class WorkingMemoryInspector {
    private final KieSession kieSession;

    public WorkingMemoryInspector(KieSession kieSession) {
        this.kieSession = kieSession;
    }

    public List<String> snapshotFacts() {
        List<String> rows = new ArrayList<>();
        kieSession.getObjects().forEach(o -> rows.add(o.toString()));
        return rows;
    }

    public List<String> queryCurrentDecision() {
        QueryResults results = kieSession.getQueryResults("Current Driving Decision");
        List<String> rows = new ArrayList<>();
        results.forEach(row -> rows.add(String.valueOf(row.get("$decision"))));
        return rows;
    }

    public List<WorkingMemoryFactView> snapshotFactViews() {
        List<WorkingMemoryFactView> rows = new ArrayList<>();
        kieSession.getObjects().forEach(o -> rows.add(toView(o)));
        return rows;
    }

    private WorkingMemoryFactView toView(Object object) {
        String type = object.getClass().getSimpleName();
        String source = "WORKING_MEMORY";
        Long timestamp = null;
        String stream = "-";

        if (object instanceof BaseEvent event) {
            source = event.getSourceId();
            timestamp = event.getTimestamp();
            stream = inferStream(type);
        } else {
            timestamp = readTimestampIfAvailable(object);
        }

        return new WorkingMemoryFactView(
            type,
            object.toString(),
            source,
            timestamp,
            stream,
            object.toString()
        );
    }

    private Long readTimestampIfAvailable(Object object) {
        try {
            Method method = object.getClass().getMethod("getTimestamp");
            Object value = method.invoke(object);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String inferStream(String type) {
        if (type.contains("SpeedLimit") || type.contains("StopSign") || type.contains("YieldSign")
            || type.contains("Pothole") || type.contains("Roadworks") || type.contains("Obstacle")
            || type.contains("Barrier") || type.contains("Roundabout")) {
            return "ROAD_STREAM";
        }
        if (type.contains("VehicleSpeed")) {
            return "VEHICLE_STREAM";
        }
        if (type.contains("Acceleration") || type.contains("LaneChangeRequest")) {
            return "COMMAND_STREAM";
        }
        if (type.contains("TTC") || type.contains("FrontDistance") || type.contains("RelativeSpeed") || type.contains("LaneOccupancy") || type.contains("Pedestrian")) {
            return "PERCEPTION_STREAM";
        }
        if (type.contains("Weather") || type.contains("Visibility") || type.contains("Grip")) {
            return "ENVIRONMENT_STREAM";
        }
        if (type.contains("TrafficLight")) {
            return "TRAFFIC_STREAM";
        }
        return "-";
    }
}
