package com.one2drive.projeto1.drools;

import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.ReducedGripEvent;
import com.one2drive.projeto1.drools.events.ReducedVisibilityEvent;
import com.one2drive.projeto1.drools.events.RoadGripEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.VehicleSpeedEvent;
import com.one2drive.projeto1.drools.events.VisibilityEvent;
import com.one2drive.projeto1.drools.events.WeatherEvent;
import com.one2drive.projeto1.drools.events.YieldSignEvent;
import org.kie.api.runtime.KieSession;

public class EventRouter {
    private final KieSession kieSession;

    public EventRouter(KieSession kieSession) {
        this.kieSession = kieSession;
    }

    public void insertEvent(Object event) {
        if (event instanceof VehicleSpeedEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.VEHICLE_STREAM).insert(event);
        } else if (event instanceof FrontDistanceEvent || event instanceof RelativeSpeedEvent || event instanceof TTCEvent
            || event instanceof PedestrianDetectedEvent || event instanceof LaneOccupancyEvent
            || event instanceof com.one2drive.projeto1.drools.events.SpeedRadarEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.PERCEPTION_STREAM).insert(event);
        } else if (event instanceof SpeedLimitDetectedEvent || event instanceof StopSignEvent
            || event instanceof ObstacleDetectedEvent || event instanceof PotholeDetectedEvent
            || event instanceof BarrierDetectedEvent || event instanceof YieldSignEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.ROAD_STREAM).insert(event);
        } else if (event instanceof TrafficLightEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.TRAFFIC_STREAM).insert(event);
        } else if (event instanceof WeatherEvent || event instanceof VisibilityEvent || event instanceof RoadGripEvent
            || event instanceof ReducedVisibilityEvent || event instanceof ReducedGripEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.ENVIRONMENT_STREAM).insert(event);
        } else if (event instanceof AccelerationRequestEvent || event instanceof LaneChangeRequestEvent) {
            kieSession.getEntryPoint(DroolsEntryPoints.COMMAND_STREAM).insert(event);
        } else {
            kieSession.insert(event);
        }
    }
}
