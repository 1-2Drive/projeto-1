package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.Barrier;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.Pothole;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.Roadworks;
import com.one2drive.projeto1.domain.Roundabout;
import com.one2drive.projeto1.domain.SpeedLimitSign;
import com.one2drive.projeto1.domain.StaticObstacle;
import com.one2drive.projeto1.domain.TrafficLight;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.KnowledgeSession;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.RoadworksDetectedEvent;
import com.one2drive.projeto1.drools.events.RoundaboutDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.drools.events.YieldSignEvent;
import com.one2drive.projeto1.drools.facts.CurrentSpeedLimit;
import com.one2drive.projeto1.drools.facts.VehicleStateFact;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioFactory;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioTimedAction;
import com.one2drive.projeto1.sensors.BlindSpotSensor;
import com.one2drive.projeto1.sensors.CameraSensor;
import com.one2drive.projeto1.sensors.EnvironmentSensor;
import com.one2drive.projeto1.sensors.FrontRadarSensor;
import com.one2drive.projeto1.sensors.SensorManager;
import com.one2drive.projeto1.sensors.SpeedSensor;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.time.SessionPseudoClock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SimulationEngine implements AutoCloseable {
    private final SimulationConfig config;
    private final SimulationClock simulationClock;
    private KnowledgeSession knowledgeSession;
    private final WorldPhysicsEngine physicsEngine;
    private final DecisionActuator actuator;
    private final SensorManager sensorManager;
    private final List<ScenarioTimedAction> timedActions;
    private final List<Runnable> pendingWorldMutations;
    private final Map<String, String> lastSensorValues;
    private final SensorSignificance sensorSignificance;
    private SensorTelemetry sensorTelemetry;
    private String previousDecisionAction;
    private String trackedDecisionAction;
    private String tracedDecisionSignature;
    private DrivingAction tracedActuatorAction;
    private double tracedActuatorAcceleration = Double.NaN;
    private long decisionChangedAtMillis;
    private final Map<String, Long> detectedObjectTimestamps;
    private final Map<String, Long> cameraDetectedObjectTimestamps;
    private final Map<String, Long> radarDetectedObjectTimestamps;
    private final Set<String> demoEvidence;
    private final boolean pseudoClock;
    private final Consumer<DrivingDecision> decisionConsumer;

    private WorldState worldState;
    private long simTimeMillis;
    private long scenarioActionCursor;
    private SimulationStatus status;
    private DrivingDecision currentDecision;
    private FactHandle vehicleStateHandle;
    private VehicleStateFact vehicleStateFact;
    private String scenarioId;

    public SimulationEngine(SimulationConfig config,
                            ScenarioDefinition scenarioDefinition,
                            boolean pseudoClock,
                            Consumer<DrivingDecision> decisionConsumer) {
        this.config = config;
        this.pseudoClock = pseudoClock;
        this.decisionConsumer = decisionConsumer;
        this.simulationClock = pseudoClock ? new PseudoSimulationClock(0) : new RealtimeSimulationClock();
        this.physicsEngine = new WorldPhysicsEngine(config);
        this.actuator = new DecisionActuator(config);
        this.pendingWorldMutations = new CopyOnWriteArrayList<>();
        this.lastSensorValues = new HashMap<>();
        this.sensorSignificance = new SensorSignificance();
        this.sensorTelemetry = SensorTelemetry.empty(telemetryConfig());
        this.trackedDecisionAction = null;
        this.tracedDecisionSignature = null;
        this.decisionChangedAtMillis = 0L;
        this.detectedObjectTimestamps = new HashMap<>();
        this.cameraDetectedObjectTimestamps = new HashMap<>();
        this.radarDetectedObjectTimestamps = new HashMap<>();
        this.demoEvidence = new HashSet<>();

        this.knowledgeSession = createKnowledgeSession();

        this.worldState = ScenarioFactory.buildWorld(scenarioDefinition, config);
        this.scenarioId = scenarioDefinition.getId();
        this.worldState.setSimTimeMillis(0);
        this.simTimeMillis = 0;
        this.status = SimulationStatus.STOPPED;
        this.currentDecision = knowledgeSession.getDrivingDecision();

        this.timedActions = new ArrayList<>(scenarioDefinition.getTimedActions());
        this.timedActions.sort(Comparator.comparingLong(ScenarioTimedAction::getTriggerTimeMillis));
        this.scenarioActionCursor = 0;

        this.sensorManager = new SensorManager(List.of(
            new SpeedSensor(config),
            new FrontRadarSensor(config, 42L),
            new CameraSensor(config),
            new BlindSpotSensor(config),
            new EnvironmentSensor(config)
        ));

        insertInitialVehicleFact();
    }

    private KnowledgeSession createKnowledgeSession() {
        ExplanationTrace trace = new ExplanationTrace(500);
        return pseudoClock
            ? KnowledgeSession.createPseudoClock(trace, decisionConsumer)
            : KnowledgeSession.createRealtime(trace, decisionConsumer);
    }

    private void insertInitialVehicleFact() {
        this.vehicleStateFact = new VehicleStateFact(
            worldState.getEgo().getSpeedKmh(),
            worldState.getEgo().getPositionMeters(),
            worldState.getEgo().getLane(),
            simTimeMillis
        );
        this.vehicleStateHandle = knowledgeSession.getKieSession().insert(vehicleStateFact);
    }

    public synchronized void start() {
        if (status == SimulationStatus.STOPPED || status == SimulationStatus.PAUSED) {
            status = SimulationStatus.RUNNING;
        }
    }

    public synchronized void pause() {
        if (status == SimulationStatus.RUNNING) {
            status = SimulationStatus.PAUSED;
        }
    }

    public synchronized void stop() {
        status = SimulationStatus.STOPPED;
    }

    public synchronized SimulationStatus getStatus() {
        return status;
    }

    public synchronized void reset(ScenarioDefinition scenarioDefinition) {
        pendingWorldMutations.clear();
        knowledgeSession.close();
        knowledgeSession = createKnowledgeSession();
        simulationClock.reset();
        worldState = ScenarioFactory.buildWorld(scenarioDefinition, config);
        scenarioId = scenarioDefinition.getId();
        worldState.setSimTimeMillis(0);
        simTimeMillis = 0;
        scenarioActionCursor = 0;
        timedActions.clear();
        timedActions.addAll(scenarioDefinition.getTimedActions());
        timedActions.sort(Comparator.comparingLong(ScenarioTimedAction::getTriggerTimeMillis));
        status = SimulationStatus.STOPPED;
        currentDecision = knowledgeSession.getDrivingDecision();
        currentDecision.reset(0);
        sensorManager.reset();
        lastSensorValues.clear();
        sensorSignificance.reset();
        sensorTelemetry = SensorTelemetry.empty(telemetryConfig());
        previousDecisionAction = null;
        trackedDecisionAction = null;
        tracedDecisionSignature = null;
        tracedActuatorAction = null;
        tracedActuatorAcceleration = Double.NaN;
        decisionChangedAtMillis = 0L;
        detectedObjectTimestamps.clear();
        cameraDetectedObjectTimestamps.clear();
        radarDetectedObjectTimestamps.clear();
        demoEvidence.clear();
        insertInitialVehicleFact();
    }

    public synchronized SimulationSnapshot tick() {
        if (status != SimulationStatus.RUNNING) {
            return snapshot();
        }

        // Keep COMPLETED visible for the snapshot that reports the completion;
        // the next running tick starts the next lifecycle in IDLE.
        normalizeCompletedManeuver();

        double dtSeconds = config.getTickMillis() / 1000.0;
        physicsEngine.updateWorld(worldState, dtSeconds);

        simTimeMillis += config.getTickMillis();
        simulationClock.advanceBy(config.getTickMillis());
        worldState.setSimTimeMillis(simTimeMillis);

        if (simulationClock.isPseudo()) {
            SessionPseudoClock pseudoClock = (SessionPseudoClock) knowledgeSession.getKieSession().getSessionClock();
            pseudoClock.advanceTime(config.getTickMillis(), TimeUnit.MILLISECONDS);
        }

        flushPendingMutations();
        runTimedActions();

        updateVehicleStateFact();

        List<Object> events = sensorManager.observe(worldState, simTimeMillis);
        knowledgeSession.getDecisionContext().beginCycle(simTimeMillis, worldState.getEgo().getSpeedKmh());
        for (Object event : events) {
            // Raw samples still go to Drools and Working Memory; only meaningful
            // transitions reach the inference timeline.
            String significant = sensorSignificance.classify(event);
            if (significant != null) {
                knowledgeSession.getTrace().add(simTimeMillis, TimelineEntryType.PERCEPTION_CHANGE, significant);
            }
            knowledgeSession.getRouter().insertEvent(event);
            updateSensorPanel(event);
            recordDetectedObject(event);
        }
        refreshSensorTelemetry(events);

        knowledgeSession.getKieSession().fireAllRules();
        knowledgeSession.getDecisionContext().applyTo(currentDecision);

        FactHandle decisionHandle = findFactHandle(currentDecision);
        if (decisionHandle != null) {
            knowledgeSession.getKieSession().update(decisionHandle, currentDecision);
        }

        boolean decisionChanged = trackDecisionTransition();
        if (decisionChanged) {
            knowledgeSession.getTrace().addDecision(currentDecision);
        }
        LaneChangeState stateBefore = worldState.getEgo().getLaneChangeState();
        actuator.applyDecision(worldState, currentDecision);
        recordManeuverStart(stateBefore);
        recordActuatorChange();
        settleManeuver();
        markDemoEvidence(events);
        purgeExpiredDetections();

        return snapshot();
    }

    /** Emits the maneuver lifecycle entry the moment Drools' decision starts execution. */
    private void recordManeuverStart(LaneChangeState stateBefore) {
        EgoVehicleState ego = worldState.getEgo();
        if (ego.getLaneChangeState() != LaneChangeState.REQUESTED || stateBefore == LaneChangeState.REQUESTED) {
            return;
        }
        knowledgeSession.getTrace().add(
            simTimeMillis,
            TimelineEntryType.MANEUVER,
            "MANEUVER STARTED " + ego.getLane().name() + " -> " + ego.getTargetLane().name()
                + " lane (lateral " + String.format("%.2f", ego.getLateralPositionMeters()) + "m)"
        );
    }


    private SensorTelemetry.SimulationConfigView telemetryConfig() {
        return new SensorTelemetry.SimulationConfigView(
            config.getRadarRangeMeters(),
            config.getCameraRangeMeters(),
            config.getBlindSpotRangeMeters()
        );
    }

    /**
     * Rebuilds the structured sensor view from the events produced this tick, so the
     * UI shows the same measurements Drools received instead of parsing text.
     */
    private void refreshSensorTelemetry(List<Object> events) {
        SensorTelemetry previous = sensorTelemetry;

        SensorTelemetry.RadarView radar = previous.frontRadar();
        if (!"none".equals(radar.targetId())
            && !radarDetectedObjectTimestamps.containsKey(radar.targetId())) {
            radar = SensorTelemetry.RadarView.empty(config.getRadarRangeMeters());
        }
        boolean radarUpdated = false;
        for (Object event : events) {
            if (event instanceof FrontDistanceEvent distance) {
                radarUpdated = true;
                radar = new SensorTelemetry.RadarView(
                    config.getRadarRangeMeters(),
                    distance.getTargetId(),
                    distance.getDistanceMeters(),
                    previous.frontRadar().relativeSpeedKmh(),
                    previous.frontRadar().ttcSeconds(),
                    "TRACKING"
                );
            } else if (event instanceof RelativeSpeedEvent relative) {
                radarUpdated = true;
                radar = new SensorTelemetry.RadarView(
                    radar.rangeMeters(),
                    relative.getTargetId(),
                    radar.distanceMeters(),
                    relative.getRelativeSpeedMps() * 3.6,
                    radar.ttcSeconds(),
                    "TRACKING"
                );
            } else if (event instanceof TTCEvent ttc) {
                radarUpdated = true;
                radar = new SensorTelemetry.RadarView(
                    radar.rangeMeters(),
                    ttc.getTargetId(),
                    radar.distanceMeters(),
                    radar.relativeSpeedKmh(),
                    ttc.getTtcSeconds(),
                    radarStatus(ttc.getTtcSeconds())
                );
            }
        }

        if (!radarUpdated && previous.frontRadar() == null) {
            radar = SensorTelemetry.RadarView.empty(config.getRadarRangeMeters());
        }

        List<SensorTelemetry.CameraDetection> detections = new ArrayList<>(
            previous.frontCamera().detections().stream()
                .filter(d -> cameraDetectedObjectTimestamps.containsKey(d.objectId()))
                .toList()
        );
        boolean cameraUpdated = false;
        for (Object event : events) {
            if (event instanceof SpeedLimitDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getSignId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getSignId(), "Speed limit " + e.getLimitKmh() + " km/h",
                    e.getDistanceMeters(), "CAMERA", "camera"));
            } else if (event instanceof PotholeDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getPotholeId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getPotholeId(), "Pothole", e.getDistanceMeters(), e.getLane().name(), "camera"));
            } else if (event instanceof ObstacleDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getObstacleId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getObstacleId(), "Obstacle", e.getDistanceMeters(), e.getLane().name(), "camera"));
            } else if (event instanceof RoadworksDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getRoadworksId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getRoadworksId(), "Roadworks", e.getDistanceMeters(), "CENTER", "camera"));
            } else if (event instanceof PedestrianDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getPedestrianId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getPedestrianId(), "Pedestrian", e.getDistanceMeters(), "CENTER", "camera"));
            }
        }
        if (!cameraUpdated && detections.isEmpty() && previous.frontCamera().detections().isEmpty()) {
            detections = new ArrayList<>();
        }
        SensorTelemetry.CameraView camera = new SensorTelemetry.CameraView(
            config.getCameraRangeMeters(), List.copyOf(detections), detections.isEmpty() ? "CLEAR" : "DETECTED");

        SensorTelemetry.BlindSpotView left = previous.blindSpotLeft();
        SensorTelemetry.BlindSpotView right = previous.blindSpotRight();
        for (Object event : events) {
            if (event instanceof LaneOccupancyEvent occupancy) {
                SensorTelemetry.BlindSpotView view = new SensorTelemetry.BlindSpotView(
                    config.getBlindSpotRangeMeters(),
                    occupancy.isOccupied(),
                    occupancy.isOccupied() ? "occupied" : "none",
                    occupancy.getNearestDistanceMeters(),
                    occupancy.isOccupied() ? "OCCUPIED" : "CLEAR"
                );
                if (occupancy.getLane() == com.one2drive.projeto1.domain.Lane.LEFT) {
                    left = view;
                } else if (occupancy.getLane() == com.one2drive.projeto1.domain.Lane.RIGHT) {
                    right = view;
                }
            }
        }

        SensorTelemetry.EnvironmentView environment = new SensorTelemetry.EnvironmentView(
            worldState.getWeatherState().getCondition().name(),
            config.getCameraRangeMeters() * worldState.getWeatherState().getVisibilityFactor(),
            worldState.getWeatherState().getGripFactor() * 100.0,
            worldState.getWeatherState().getCondition() != WeatherCondition.CLEAR
        );

        sensorTelemetry = new SensorTelemetry(radar, camera, left, right, environment);
    }

    private void normalizeCompletedManeuver() {
        if (worldState.getEgo().getLaneChangeState() == LaneChangeState.COMPLETED) {
            worldState.getEgo().setLaneChangeState(LaneChangeState.IDLE);
        }
    }

    private static String radarStatus(double ttcSeconds) {
        if (!Double.isFinite(ttcSeconds)) {
            return "TRACKING";
        }
        if (ttcSeconds < 2.0) {
            return "CRITICAL";
        }
        if (ttcSeconds < 5.0) {
            return "WARNING";
        }
        return "TRACKING";
    }

    /** Records when the active decision actually changed so the UI can show a transition. */
    private boolean trackDecisionTransition() {
        String action = currentDecision.getAction().name();
        String signature = action + "|" + currentDecision.getRiskLevel()
            + "|" + currentDecision.getSourceRule() + "|"
            + String.format("%.1f", currentDecision.getRecommendedSpeedKmh());
        if (trackedDecisionAction == null) {
            trackedDecisionAction = action;
            previousDecisionAction = null;
            tracedDecisionSignature = signature;
            decisionChangedAtMillis = simTimeMillis;
            return true;
        }
        if (!signature.equals(tracedDecisionSignature)) {
            previousDecisionAction = trackedDecisionAction;
            trackedDecisionAction = action;
            tracedDecisionSignature = signature;
            decisionChangedAtMillis = simTimeMillis;
            return true;
        } else if (!action.equals(trackedDecisionAction)) {
            previousDecisionAction = trackedDecisionAction;
            trackedDecisionAction = action;
            decisionChangedAtMillis = simTimeMillis;
        }
        return false;
    }

    /** Keeps one actuation entry per meaningful control change, not one per physics tick. */
    private void recordActuatorChange() {
        double acceleration = worldState.getEgo().getAccelerationMps2();
        boolean actionChanged = currentDecision.getAction() != tracedActuatorAction;
        boolean accelerationChanged = Double.isNaN(tracedActuatorAcceleration)
            || Math.abs(acceleration - tracedActuatorAcceleration) >= 0.25;
        if (!actionChanged && !accelerationChanged) {
            return;
        }
        tracedActuatorAction = currentDecision.getAction();
        tracedActuatorAcceleration = acceleration;
        knowledgeSession.getTrace().add(
            simTimeMillis,
            TimelineEntryType.ACTUATOR,
            "ACTUATOR " + currentDecision.getAction() + " acceleration=" + acceleration
        );
    }

    private void updateVehicleStateFact() {
        EgoVehicleState ego = worldState.getEgo();
        vehicleStateFact.setSpeedKmh(ego.getSpeedKmh());
        vehicleStateFact.setPositionMeters(ego.getPositionMeters());
        vehicleStateFact.setLane(ego.getLane());
        vehicleStateFact.setLaneChangeState(ego.getLaneChangeState());
        vehicleStateFact.setTargetLane(ego.getTargetLane());
        vehicleStateFact.setTimestamp(simTimeMillis);
        knowledgeSession.getKieSession().update(vehicleStateHandle, vehicleStateFact);
    }

    /**
     * Settles a finished maneuver: the committed lane becomes the lane the vehicle
     * physically reached, so sensors and rules reason about where the car actually is.
     */
    private void settleManeuver() {
        EgoVehicleState ego = worldState.getEgo();
        if (ego.getLaneChangeState() != LaneChangeState.COMPLETED) {
            return;
        }
        ego.setLane(ego.getTargetLane());
        knowledgeSession.getTrace().add(
            simTimeMillis,
            TimelineEntryType.MANEUVER,
            "MANEUVER COMPLETED " + ego.getLane().name() + " lane at "
                + String.format("%.2f", ego.getLateralPositionMeters()) + "m lateral"
        );
    }

    private FactHandle findFactHandle(Object object) {
        for (FactHandle handle : knowledgeSession.getKieSession().getFactHandles()) {
            Object candidate = knowledgeSession.getKieSession().getObject(handle);
            if (candidate == object) {
                return handle;
            }
        }
        return null;
    }

    private void flushPendingMutations() {
        List<Runnable> copy = new ArrayList<>(pendingWorldMutations);
        pendingWorldMutations.clear();
        copy.forEach(Runnable::run);
    }

    private void runTimedActions() {
        while (scenarioActionCursor < timedActions.size()) {
            ScenarioTimedAction action = timedActions.get((int) scenarioActionCursor);
            if (action.getTriggerTimeMillis() > simTimeMillis) {
                break;
            }
            executeTimedAction(action);
            scenarioActionCursor++;
        }
    }

    private void executeTimedAction(ScenarioTimedAction action) {
        switch (action.getType()) {
            case SET_RAIN -> setRain(true);
            case CLEAR_WEATHER -> setRain(false);
            case ADD_ROAD_ELEMENT -> {
                if (action.getRoadElement() != null) {
                    addRoadElementAhead(action.getRoadElement());
                }
            }
            case ADD_ACTOR -> {
                if (action.getActor() != null) {
                    addActorAhead(action.getActor());
                }
            }
            case REQUEST_LANE_CHANGE_LEFT -> requestLaneChange(LaneChangeDirection.LEFT);
            case REQUEST_LANE_CHANGE_RIGHT -> requestLaneChange(LaneChangeDirection.RIGHT);
            case REQUEST_ACCELERATION -> requestAcceleration(action.getValue() == null ? config.getDefaultCruiseSpeedKmh() : action.getValue());
        }
    }

    private void updateSensorPanel(Object event) {
        String key = event.getClass().getSimpleName();
        lastSensorValues.put(key, FactDescription.describe(event));
    }

    private void recordDetectedObject(Object event) {
        String objectId = null;
        if (event instanceof SpeedLimitDetectedEvent e) objectId = e.getSignId();
        else if (event instanceof TrafficLightEvent e) objectId = e.getLightId();
        else if (event instanceof StopSignEvent e) objectId = e.getSignId();
        else if (event instanceof YieldSignEvent e) objectId = e.getSignId();
        else if (event instanceof PedestrianDetectedEvent e) objectId = e.getPedestrianId();
        else if (event instanceof PotholeDetectedEvent e) objectId = e.getPotholeId();
        else if (event instanceof BarrierDetectedEvent e) objectId = e.getBarrierId();
        else if (event instanceof RoundaboutDetectedEvent e) objectId = e.getRoundaboutId();
        else if (event instanceof RoadworksDetectedEvent e) objectId = e.getRoadworksId();
        else if (event instanceof ObstacleDetectedEvent e) objectId = e.getObstacleId();
        else if (event instanceof FrontDistanceEvent e) objectId = e.getTargetId();
        else if (event instanceof RelativeSpeedEvent e) objectId = e.getTargetId();
        else if (event instanceof TTCEvent e) objectId = e.getTargetId();
        if (objectId != null && !objectId.isBlank()) {
            detectedObjectTimestamps.put(objectId, simTimeMillis);
            if (event instanceof FrontDistanceEvent || event instanceof RelativeSpeedEvent || event instanceof TTCEvent) {
                radarDetectedObjectTimestamps.put(objectId, simTimeMillis);
            } else {
                cameraDetectedObjectTimestamps.put(objectId, simTimeMillis);
            }
        }
    }

    private void purgeExpiredDetections() {
        long expiry = 600L;
        detectedObjectTimestamps.entrySet().removeIf(entry -> simTimeMillis - entry.getValue() > expiry);
        cameraDetectedObjectTimestamps.entrySet().removeIf(entry -> simTimeMillis - entry.getValue() > expiry);
        radarDetectedObjectTimestamps.entrySet().removeIf(entry -> simTimeMillis - entry.getValue() > expiry);
    }

    private void markDemoEvidence(List<Object> events) {
        if (simTimeMillis > 0) {
            demoEvidence.add("normal");
        }
        boolean hasSpeedLimit = events.stream().anyMatch(SpeedLimitDetectedEvent.class::isInstance);
        boolean hasReducedWeather = events.stream().anyMatch(event -> event instanceof com.one2drive.projeto1.drools.events.ReducedVisibilityEvent
            || event instanceof com.one2drive.projeto1.drools.events.ReducedGripEvent);
        boolean hasTtc = events.stream().anyMatch(TTCEvent.class::isInstance);
        boolean hasYieldOrRoundabout = events.stream().anyMatch(event -> event instanceof YieldSignEvent || event instanceof RoundaboutDetectedEvent);
        boolean hasPedestrian = events.stream().anyMatch(PedestrianDetectedEvent.class::isInstance);
        DrivingAction action = currentDecision.getAction();

        if (hasSpeedLimit && action == DrivingAction.REDUCE_SPEED) {
            demoEvidence.add("speed-limit");
        }
        if (hasReducedWeather && action == DrivingAction.REDUCE_SPEED) {
            demoEvidence.add("weather");
        }
        if (hasTtc && action == DrivingAction.EMERGENCY_BRAKE) {
            demoEvidence.add("ttc");
        }
        if (hasYieldOrRoundabout && (action == DrivingAction.REDUCE_SPEED || action == DrivingAction.WAIT
            || action == DrivingAction.CONTINUE)) {
            demoEvidence.add("yield");
        }
        if (hasPedestrian && (action == DrivingAction.BRAKE || action == DrivingAction.STOP
            || action == DrivingAction.EMERGENCY_BRAKE)) {
            demoEvidence.add("pedestrian");
        }
    }

    private void enqueueWorldMutation(Runnable mutation) {
        if (status == SimulationStatus.RUNNING) {
            pendingWorldMutations.add(mutation);
            return;
        }
        mutation.run();
    }

    public synchronized void addRoadElementAhead(ScenarioRoadElementDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioRoadElementDefinition d = cloneRoadElement(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        enqueueWorldMutation(() -> worldState.addRoadElement(ScenarioFactory.createRoadElement(d)));
    }

    public synchronized String addRoadElementAheadAndGetId(ScenarioRoadElementDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioRoadElementDefinition d = cloneRoadElement(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        RoadElement element = ScenarioFactory.createRoadElement(d);
        enqueueWorldMutation(() -> worldState.addRoadElement(element));
        return element.getId();
    }

    public synchronized void addActorAhead(ScenarioActorDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioActorDefinition d = cloneActor(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        enqueueWorldMutation(() -> worldState.addActor(ScenarioFactory.createActor(d)));
    }

    public synchronized String addActorAheadAndGetId(ScenarioActorDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioActorDefinition d = cloneActor(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        DynamicActor actor = ScenarioFactory.createActor(d);
        enqueueWorldMutation(() -> worldState.addActor(actor));
        return actor.getId();
    }

    public synchronized boolean updateRoadElementById(String id, ScenarioRoadElementDefinition definition) {
        Optional<RoadElement> maybeElement = worldState.findRoadElementById(id);
        if (maybeElement.isEmpty()) {
            return false;
        }
        RoadElement element = maybeElement.get();
        enqueueWorldMutation(() -> applyRoadElementUpdate(element, definition));
        return true;
    }

    public synchronized boolean updateActorById(String id, ScenarioActorDefinition definition) {
        Optional<DynamicActor> maybeActor = worldState.findActorById(id);
        if (maybeActor.isEmpty()) {
            return false;
        }
        DynamicActor actor = maybeActor.get();
        enqueueWorldMutation(() -> applyActorUpdate(actor, definition));
        return true;
    }

    public synchronized boolean removeWorldObjectById(String id) {
        boolean roadExists = worldState.findRoadElementById(id).isPresent();
        boolean actorExists = worldState.findActorById(id).isPresent();
        if (!roadExists && !actorExists) {
            return false;
        }
        enqueueWorldMutation(() -> {
            if (roadExists) {
                worldState.removeRoadElementById(id);
            }
            if (actorExists) {
                worldState.removeActorById(id);
            }
        });
        return true;
    }

    private ScenarioRoadElementDefinition cloneRoadElement(ScenarioRoadElementDefinition src) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(src.getType());
        d.setPositionMeters(src.getPositionMeters());
        d.setLane(src.getLane());
        d.setSpeedLimitKmh(src.getSpeedLimitKmh());
        d.setTrafficLightState(src.getTrafficLightState());
        d.setSeverity(src.getSeverity());
        d.setBarrierOpen(src.getBarrierOpen());
        d.setRoundaboutApproachSpeedKmh(src.getRoundaboutApproachSpeedKmh());
        return d;
    }

    private ScenarioActorDefinition cloneActor(ScenarioActorDefinition src) {
        ScenarioActorDefinition d = new ScenarioActorDefinition();
        d.setType(src.getType());
        d.setPositionMeters(src.getPositionMeters());
        d.setLane(src.getLane());
        d.setSpeedKmh(src.getSpeedKmh());
        return d;
    }

    public synchronized void setRain(boolean rain) {
        enqueueWorldMutation(() -> {
            if (rain) {
                worldState.getWeatherState().setCondition(WeatherCondition.RAIN);
                worldState.getWeatherState().setVisibilityFactor(0.7);
                worldState.getWeatherState().setGripFactor(0.65);
            } else {
                worldState.getWeatherState().setCondition(WeatherCondition.CLEAR);
                worldState.getWeatherState().setVisibilityFactor(1.0);
                worldState.getWeatherState().setGripFactor(1.0);
            }
        });
    }

    public synchronized void setWeatherFactors(double visibilityFactor, double gripFactor) {
        enqueueWorldMutation(() -> {
            worldState.getWeatherState().setVisibilityFactor(Math.max(0.2, Math.min(1.0, visibilityFactor)));
            worldState.getWeatherState().setGripFactor(Math.max(0.2, Math.min(1.0, gripFactor)));
            if (worldState.getWeatherState().getVisibilityFactor() < 0.95 || worldState.getWeatherState().getGripFactor() < 0.95) {
                worldState.getWeatherState().setCondition(WeatherCondition.RAIN);
            } else {
                worldState.getWeatherState().setCondition(WeatherCondition.CLEAR);
            }
        });
    }

    public synchronized void requestLaneChange(LaneChangeDirection direction) {
        knowledgeSession.getRouter().insertEvent(new LaneChangeRequestEvent(simTimeMillis, "ui", direction));
    }

    public synchronized void requestAcceleration(double targetSpeedKmh) {
        knowledgeSession.getRouter().insertEvent(new AccelerationRequestEvent(simTimeMillis, "ui", targetSpeedKmh));
    }

    public synchronized SensorTelemetry getSensorTelemetry() {
        return sensorTelemetry;
    }

    public synchronized String getPreviousDecisionAction() {
        return previousDecisionAction;
    }

    public synchronized long getDecisionChangedAtMillis() {
        return decisionChangedAtMillis;
    }

    public synchronized SimulationSnapshot snapshot() {
        double legalLimit = 120;
        for (Object obj : knowledgeSession.getKieSession().getObjects(new ClassObjectFilter(CurrentSpeedLimit.class))) {
            CurrentSpeedLimit limit = (CurrentSpeedLimit) obj;
            legalLimit = limit.getLimitKmh();
            break;
        }

        List<String> timeline = new ArrayList<>();
        for (TimelineEntry entry : knowledgeSession.getTrace().snapshot()) {
            timeline.add(entry.toString());
        }

        return new SimulationSnapshot(
            simTimeMillis,
            status,
            worldState.getEgo().getSpeedKmh(),
            worldState.getEgo().getPositionMeters(),
            worldState.getEgo().getLane().name(),
            worldState.getEgo().getLateralPositionMeters(),
            worldState.getEgo().getTargetLane().name(),
            worldState.getEgo().getLaneChangeState().name(),
            worldState.getEgo().getLaneChangeProgress(),
            legalLimit,
            currentDecision.getRecommendedSpeedKmh(),
            currentDecision.getAction().name(),
            currentDecision.getRiskLevel().name(),
            currentDecision.getReason(),
            currentDecision.getSourceRule(),
            Map.copyOf(lastSensorValues),
            knowledgeSession.getInspector().snapshotFacts(),
            timeline,
            detectedObjectTimestamps.keySet().stream().sorted().toList(),
            cameraDetectedObjectTimestamps.keySet().stream().sorted().toList(),
            radarDetectedObjectTimestamps.keySet().stream().sorted().toList()
        );
    }

    private void applyRoadElementUpdate(RoadElement element, ScenarioRoadElementDefinition definition) {
        if (definition.getLane() != null) {
            element.setLane(definition.getLane());
        }
        double base = worldState.getEgo().getPositionMeters();
        element.setPositionMeters(base + definition.getPositionMeters());

        if (element instanceof SpeedLimitSign sign && definition.getSpeedLimitKmh() != null) {
            sign.setLimitKmh(definition.getSpeedLimitKmh());
        }
        if (element instanceof Pothole pothole && definition.getSeverity() != null) {
            pothole.setSeverity(definition.getSeverity());
        }
        if (element instanceof Roadworks roadworks && definition.getSeverity() != null) {
            roadworks.setSeverity(definition.getSeverity());
        }
        if (element instanceof StaticObstacle obstacle && definition.getSeverity() != null) {
            obstacle.setSeverity(definition.getSeverity());
        }
        if (element instanceof TrafficLight trafficLight && definition.getTrafficLightState() != null) {
            trafficLight.setState(definition.getTrafficLightState());
        }
        if (element instanceof Barrier barrier && definition.getBarrierOpen() != null) {
            barrier.setOpen(definition.getBarrierOpen());
        }
        if (element instanceof Roundabout roundabout && definition.getRoundaboutApproachSpeedKmh() != null) {
            roundabout.setRecommendedApproachSpeedKmh(definition.getRoundaboutApproachSpeedKmh());
        }
    }

    private void applyActorUpdate(DynamicActor actor, ScenarioActorDefinition definition) {
        if (definition.getLane() != null) {
            actor.setLane(definition.getLane());
        }
        double base = worldState.getEgo().getPositionMeters();
        actor.setPositionMeters(base + definition.getPositionMeters());
        actor.setSpeedMps(Math.max(0.0, definition.getSpeedKmh()) / 3.6);
    }

    public synchronized SimulationConfig getConfig() {
        return config;
    }

    public synchronized List<WorkingMemoryFactView> getWorkingMemoryFactViews() {
        return knowledgeSession.getInspector().snapshotFactViews();
    }

    public synchronized List<TimelineEntry> getTimelineEntries() {
        return knowledgeSession.getTrace().snapshot();
    }

    public synchronized List<RoadRenderItem> getAllRoadItemsRelativeToEgo() {
        double egoPos = worldState.getEgo().getPositionMeters();
        List<RoadRenderItem> items = new ArrayList<>();

        worldState.getRoadElements().forEach(element -> items.add(new RoadRenderItem(
            element.getClass().getSimpleName(),
            element.getId(),
            element.getPositionMeters() - egoPos,
            element.getLane().name(),
            element.toString()
        )));

        worldState.getActors().forEach(actor -> items.add(new RoadRenderItem(
            actor.getClass().getSimpleName(),
            actor.getId(),
            actor.getPositionMeters() - egoPos,
            actor.getLane().name(),
            actor.toString()
        )));

        return items;
    }

    public synchronized WorldState getWorldState() {
        return worldState;
    }

    public synchronized KnowledgeSession getKnowledgeSession() {
        return knowledgeSession;
    }

    public synchronized DrivingDecision getCurrentDecision() {
        return currentDecision;
    }

    public synchronized ExplanationTrace getTrace() {
        return knowledgeSession.getTrace();
    }

    public synchronized String getScenarioId() {
        return scenarioId;
    }

    public synchronized Set<String> getDemoEvidence() {
        return Set.copyOf(demoEvidence);
    }

    public synchronized List<String> queryCurrentDecision() {
        var query = knowledgeSession.getKieSession().getQueryResults("Current Driving Decision");
        List<String> lines = new ArrayList<>();
        for (QueryResultsRow row : query) {
            lines.add(String.valueOf(row.get("$decision")));
        }
        return lines;
    }

    public synchronized List<RoadRenderItem> visibleRoadItems(double forwardMeters, double backwardMeters) {
        double egoPos = worldState.getEgo().getPositionMeters();
        List<RoadRenderItem> items = new ArrayList<>();

        worldState.getRoadElements().forEach(element -> {
            double rel = element.getPositionMeters() - egoPos;
            if (rel >= -backwardMeters && rel <= forwardMeters) {
                items.add(new RoadRenderItem(
                    element.getClass().getSimpleName(),
                    element.getId(),
                    rel,
                    element.getLane().name(),
                    element.toString()
                ));
            }
        });

        worldState.getActors().forEach(actor -> {
            double rel = actor.getPositionMeters() - egoPos;
            if (rel >= -backwardMeters && rel <= forwardMeters) {
                items.add(new RoadRenderItem(
                    actor.getClass().getSimpleName(),
                    actor.getId(),
                    rel,
                    actor.getLane().name(),
                    actor.toString()
                ));
            }
        });

        return items;
    }

    @Override
    public synchronized void close() {
        status = SimulationStatus.STOPPED;
        pendingWorldMutations.clear();
        knowledgeSession.close();
    }
}
