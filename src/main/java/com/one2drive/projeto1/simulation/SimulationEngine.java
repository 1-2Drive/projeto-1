package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.DynamicActor;
import com.one2drive.projeto1.domain.DrivingAction;
import com.one2drive.projeto1.domain.DrivingState;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.EgoVehicleState;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.LaneChangeState;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.domain.WeatherCondition;
import com.one2drive.projeto1.drools.FactDescription;
import com.one2drive.projeto1.drools.events.AccelerationRequestEvent;
import com.one2drive.projeto1.drools.events.BarrierDetectedEvent;
import com.one2drive.projeto1.drools.events.FrontDistanceEvent;
import com.one2drive.projeto1.drools.events.LaneOccupancyEvent;
import com.one2drive.projeto1.drools.DroolsBackend;
import com.one2drive.projeto1.drools.KnowledgeSession;
import com.one2drive.projeto1.drools.WorkingMemoryFactView;
import com.one2drive.projeto1.drools.events.LaneChangeRequestEvent;
import com.one2drive.projeto1.drools.events.ObstacleDetectedEvent;
import com.one2drive.projeto1.drools.events.PedestrianDetectedEvent;
import com.one2drive.projeto1.drools.events.PotholeDetectedEvent;
import com.one2drive.projeto1.drools.events.RelativeSpeedEvent;
import com.one2drive.projeto1.drools.events.SpeedLimitDetectedEvent;
import com.one2drive.projeto1.drools.events.StopSignEvent;
import com.one2drive.projeto1.drools.events.TTCEvent;
import com.one2drive.projeto1.drools.events.TrafficLightEvent;
import com.one2drive.projeto1.explanation.ExplanationTrace;
import com.one2drive.projeto1.explanation.TimelineEntry;
import com.one2drive.projeto1.explanation.TimelineEntryType;
import com.one2drive.projeto1.inference.DecisionInput;
import com.one2drive.projeto1.inference.ExpertBackendType;
import com.one2drive.projeto1.inference.ExpertSystemBackend;
import com.one2drive.projeto1.inference.ExpertSystemException;
import com.one2drive.projeto1.history.SessionSnapshot;
import com.one2drive.projeto1.history.VehicleSample;
import com.one2drive.projeto1.prolog.PrologBackend;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SimulationEngine implements AutoCloseable {
    /** Objects closer than this behind the car are drawn behind it; anything further back is drawn as coming up. */
    static final double RENDER_BEHIND_METERS = 50.0;
    /** Roughly one car length: a passed vehicle catching up from behind vanishes just before the bumpers meet. */
    private static final double CATCH_UP_GAP_METERS = 6.0;

    private final SimulationConfig config;
    private final SimulationClock simulationClock;
    private ExpertSystemBackend backend;
    private ExpertBackendType backendType;
    /** Why the active backend stopped deciding, until the next reset; null while it works. */
    private String backendError;
    private final WorldPhysicsEngine physicsEngine;
    private final DecisionActuator actuator;
    private final SensorManager sensorManager;
    private final CameraSensor cameraSensor;
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
    private final Set<String> detectedWorldObjectIds;
    private String trackedPedestrianId = "none";
    private boolean trackedPedestrianReportedCrossed;
    private final Set<String> passedActorIds;
    private final Set<String> demoEvidence;
    private final boolean pseudoClock;
    private final Consumer<DrivingDecision> decisionConsumer;
    private Consumer<SessionSnapshot> sessionSnapshotConsumer;
    private SessionSnapshot lastSessionSnapshot;

    private WorldState worldState;
    private long simTimeMillis;
    private long scenarioActionCursor;
    private SimulationStatus status;
    private DrivingDecision currentDecision;
    private String scenarioId;
    private double scenarioInitialSpeedKmh;

    public SimulationEngine(SimulationConfig config,
                            ScenarioDefinition scenarioDefinition,
                            boolean pseudoClock,
                            Consumer<DrivingDecision> decisionConsumer) {
        this(config, scenarioDefinition, pseudoClock, decisionConsumer, ExpertBackendType.DROOLS, snapshot -> { });
    }

    public SimulationEngine(SimulationConfig config,
                            ScenarioDefinition scenarioDefinition,
                            boolean pseudoClock,
                            Consumer<DrivingDecision> decisionConsumer,
                            ExpertBackendType backendType) {
        this(config, scenarioDefinition, pseudoClock, decisionConsumer, backendType, snapshot -> { });
    }

    public SimulationEngine(SimulationConfig config,
                            ScenarioDefinition scenarioDefinition,
                            boolean pseudoClock,
                            Consumer<DrivingDecision> decisionConsumer,
                            ExpertBackendType backendType,
                            Consumer<SessionSnapshot> sessionSnapshotConsumer) {
        this.config = config;
        this.backendType = backendType;
        this.pseudoClock = pseudoClock;
        this.decisionConsumer = decisionConsumer;
        this.sessionSnapshotConsumer = sessionSnapshotConsumer;
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
        this.detectedWorldObjectIds = new HashSet<>();
        this.passedActorIds = new HashSet<>();
        this.demoEvidence = new HashSet<>();

        this.worldState = ScenarioFactory.buildWorld(scenarioDefinition, config);
        this.scenarioId = scenarioDefinition.getId();
        this.worldState.setSimTimeMillis(0);
        this.simTimeMillis = 0;
        this.status = SimulationStatus.STOPPED;

        this.timedActions = new ArrayList<>(scenarioDefinition.getTimedActions());
        this.timedActions.sort(Comparator.comparingLong(ScenarioTimedAction::getTriggerTimeMillis));
        this.scenarioActionCursor = 0;

        this.scenarioInitialSpeedKmh = scenarioDefinition.getInitialSpeedKmh();
        holdVehicleAtRest();

        this.cameraSensor = new CameraSensor(config);
        this.sensorManager = new SensorManager(List.of(
            new SpeedSensor(config),
            new FrontRadarSensor(config, 42L),
            cameraSensor,
            new BlindSpotSensor(config),
            new EnvironmentSensor(config)
        ));

        this.backend = createBackend();
        this.currentDecision = backend.getDrivingDecision();
        this.lastSessionSnapshot = null;
    }

    /**
     * Opens a session of the selected backend on the world as it is now: the vehicle at rest
     * and the scenario's initial speed as the cruise speed of this road.
     */
    private ExpertSystemBackend createBackend() {
        return switch (backendType) {
            case DROOLS -> new DroolsBackend(config, pseudoClock, decisionConsumer, worldState, simTimeMillis,
                scenarioInitialSpeedKmh);
            case PROLOG -> new PrologBackend(config, decisionConsumer, scenarioInitialSpeedKmh);
        };
    }

    /**
     * A world that is not running reports the vehicle at rest: the scenario's initial
     * speed is applied by {@link #start()}, so a stopped simulation never shows a
     * cruising speed or moves without an explicit START.
     */
    private void holdVehicleAtRest() {
        EgoVehicleState ego = worldState.getEgo();
        ego.setSpeedKmh(0.0);
        ego.setDrivingState(DrivingState.STOPPED);
    }

    public synchronized void start() {
        if (status == SimulationStatus.STOPPED || status == SimulationStatus.PAUSED) {
            // A world still at rest takes the scenario's initial speed; a caller that
            // preset a speed (or a resumed simulation) keeps it untouched.
            if (simTimeMillis == 0L && scenarioInitialSpeedKmh > 0.0
                && worldState.getEgo().getSpeedKmh() <= 0.0) {
                EgoVehicleState ego = worldState.getEgo();
                ego.setSpeedKmh(scenarioInitialSpeedKmh);
                ego.setDrivingState(DrivingState.CRUISING);
            }
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
        backend.close();
        backendError = null;
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
        detectedWorldObjectIds.clear();
        trackedPedestrianId = "none";
        trackedPedestrianReportedCrossed = false;
        passedActorIds.clear();
        demoEvidence.clear();
        scenarioInitialSpeedKmh = scenarioDefinition.getInitialSpeedKmh();
        holdVehicleAtRest();
        backend = createBackend();
        currentDecision = backend.getDrivingDecision();
        currentDecision.reset(0);
        lastSessionSnapshot = null;
    }

    /**
     * Makes {@code type} the backend that decides, reloading {@code scenarioDefinition} as a
     * reset does: the new engine starts from an empty working memory, never from the other's.
     * If the new backend cannot start, the previous one is restored and the error rethrown.
     */
    public synchronized void switchBackend(ExpertBackendType type, ScenarioDefinition scenarioDefinition) {
        ExpertBackendType previous = backendType;
        backendType = type;
        try {
            reset(scenarioDefinition);
        } catch (RuntimeException e) {
            backendType = previous;
            reset(scenarioDefinition);
            throw e;
        }
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
            backend.advanceClock(config.getTickMillis());
        }

        flushPendingMutations();
        runTimedActions();

        List<Object> events = sensorManager.observe(worldState, simTimeMillis);
        backend.beginDecisionCycle(simTimeMillis, worldState.getEgo().getSpeedKmh());
        for (Object event : events) {
            // Raw samples still go to the rule engine and its working memory; only
            // meaningful transitions reach the inference timeline.
            String significant = sensorSignificance.classify(event);
            if (significant != null) {
                backend.getTrace().add(simTimeMillis, TimelineEntryType.PERCEPTION_CHANGE, significant);
            }
            backend.insertEvent(event);
            updateSensorPanel(event);
            recordDetectedObject(event);
        }
        backend.updateVehicleState(new DecisionInput(worldState, simTimeMillis, scenarioId));
        refreshSensorTelemetry(events);

        try {
            backend.decide();
        } catch (ExpertSystemException e) {
            // No decision was inferred, so none is acted on: the simulation pauses with the
            // error in view instead of driving on a default nobody decided.
            backendError = backendType.label() + " failed: " + e.getMessage();
            backend.getTrace().add(simTimeMillis, TimelineEntryType.INFO, backendError + " (simulation paused)");
            status = SimulationStatus.PAUSED;
            return snapshot();
        }

        boolean decisionChanged = trackDecisionTransition();
        if (decisionChanged) {
            backend.getTrace().addDecision(currentDecision);
        }
        SessionSnapshot contextSnapshot = buildSessionSnapshot("not yet applied");
        LaneChangeState stateBefore = worldState.getEgo().getLaneChangeState();
        actuator.applyDecision(worldState, currentDecision);
        recordManeuverStart(stateBefore);
        recordActuatorChange();
        settleManeuver();
        List<com.one2drive.projeto1.api.model.ApiTimelineEntry> cycleTimeline = backend.getTrace().snapshot().stream()
            .filter(entry -> entry.getTimestamp() == simTimeMillis)
            .map(entry -> new com.one2drive.projeto1.api.model.ApiTimelineEntry(
                entry.getTimestamp(), entry.getType().name(), entry.getMessage())).toList();
        List<com.one2drive.projeto1.api.model.ApiWorkingMemoryRow> cycleFacts = backend.factViews().stream()
            .map(fact -> new com.one2drive.projeto1.api.model.ApiWorkingMemoryRow(
                fact.type(), fact.value(), fact.source(), fact.timestamp(), fact.stream())).toList();
        // The world, sensors and vehicle are those the rules decided on; the decision is the
        // one the actuator left, so a lane change it refused is recorded as refused.
        lastSessionSnapshot = new SessionSnapshot(contextSnapshot.simulationTimeMillis(), contextSnapshot.vehicle(),
            contextSnapshot.environment(), contextSnapshot.sensors(), contextSnapshot.worldObjects(), cycleFacts, cycleTimeline,
            SessionSnapshot.DecisionSnapshot.from(currentDecision, actuatorResult()), contextSnapshot.roadMap());
        sessionSnapshotConsumer.accept(lastSessionSnapshot);
        consumePassedRoadElements();
        markDemoEvidence(events);
        purgeExpiredDetections();

        return snapshot();
    }

    private String actuatorResult() {
        EgoVehicleState ego = worldState.getEgo();
        return "action=" + currentDecision.getAction() + ", acceleration="
            + String.format("%.2f", ego.getAccelerationMps2()) + " m/s², lane=" + ego.getLane()
            + (ego.getLaneChangeState().isActive() ? " -> " + ego.getTargetLane() : "")
            + ", steering=" + ego.getSteeringState() + ", indicator=" + ego.getIndicatorState();
    }

    private SessionSnapshot buildSessionSnapshot(String actuatorResult) {
        EgoVehicleState ego = worldState.getEgo();
        List<com.one2drive.projeto1.api.model.ApiTimelineEntry> timeline = backend.getTrace().snapshot().stream()
            .filter(entry -> entry.getTimestamp() == simTimeMillis)
            .map(entry -> new com.one2drive.projeto1.api.model.ApiTimelineEntry(
                entry.getTimestamp(), entry.getType().name(), entry.getMessage())).toList();
        List<com.one2drive.projeto1.api.model.ApiRoadElement> objects = getAllRoadItemsRelativeToEgo().stream()
            .map(item -> new com.one2drive.projeto1.api.model.ApiRoadElement(item.id(), item.type(),
                item.positionMeters(), item.lane(), item.details(), item.worldPositionMeters())).toList();
        List<com.one2drive.projeto1.api.model.ApiWorkingMemoryRow> facts = backend.factViews().stream()
            .map(fact -> new com.one2drive.projeto1.api.model.ApiWorkingMemoryRow(
                fact.type(), fact.value(), fact.source(), fact.timestamp(), fact.stream())).toList();
        var egoView = new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.EgoVehicleView(
            ego.getSpeedKmh(), ego.getPositionMeters(), ego.getLane().name(), ego.getAccelerationMps2(),
            ego.getLateralPositionMeters(), ego.getTargetLane().name(), ego.getLaneChangeState().name(),
            ego.getLaneChangeProgress(), ego.getDrivingState().name(), ego.getIndicatorState().name(),
            ego.getSteeringState().name(), ego.getHeadingDegrees());
        var environment = new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.EnvironmentView(
            worldState.getWeatherState().getCondition().name(), worldState.getWeatherState().getVisibilityFactor(),
            worldState.getWeatherState().getGripFactor());
        var sensor = sessionSensorView();
        return new SessionSnapshot(simTimeMillis, egoView, environment, sensor, objects, facts, timeline,
            SessionSnapshot.DecisionSnapshot.from(currentDecision, actuatorResult), roadMapSnapshot());
    }

    private com.one2drive.projeto1.api.model.ApiSimulationSnapshot.SensorTelemetryView sessionSensorView() {
        SensorTelemetry t = sensorTelemetry;
        return new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.SensorTelemetryView(
            new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.RadarTelemetryView(
                t.frontRadar().rangeMeters(), t.frontRadar().targetId(), t.frontRadar().distanceMeters(),
                t.frontRadar().relativeSpeedKmh(), t.frontRadar().ttcSeconds(), t.frontRadar().status()),
            new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.CameraTelemetryView(
                t.frontCamera().rangeMeters(), t.frontCamera().detections().stream()
                    .map(d -> new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.CameraDetectionView(
                        d.objectId(), d.label(), d.distanceMeters(), d.lane())).toList(), t.frontCamera().status()),
            mapBlindSpot(t.blindSpotLeft()), mapBlindSpot(t.blindSpotRight()),
            new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.EnvironmentTelemetryView(
                t.environment().condition(), t.environment().effectiveRangeMeters(),
                t.environment().gripPercent(), t.environment().rain()));
    }

    private com.one2drive.projeto1.api.model.ApiSimulationSnapshot.BlindSpotTelemetryView mapBlindSpot(
        SensorTelemetry.BlindSpotView view) {
        return new com.one2drive.projeto1.api.model.ApiSimulationSnapshot.BlindSpotTelemetryView(
            view.rangeMeters(), view.occupied(), view.targetId(), view.distanceMeters(), view.status());
    }

    /** Retires single-use static objects after passing while retaining their visual as it moves behind the car. */
    private void consumePassedRoadElements() {
        for (RoadElement element : List.copyOf(worldState.getRoadElements())) {
            double distance = worldState.signedDistance(element.getPositionMeters());
            if (worldState.isLoopWorldObjects()) continue;
            if (distance <= 0.0 && detectedWorldObjectIds.contains(element.getId())) {
                element.setActive(false);
            }
            if (distance < -RENDER_BEHIND_METERS && detectedWorldObjectIds.contains(element.getId())) {
                worldState.removeRoadElementById(element.getId());
            }
        }
        // Overtaken vehicles and crossed pedestrians keep moving behind the car, where the
        // blind-spot sensor still sees them, and only leave the world once they are out of
        // the rendered window, so they never come back around the loop.
        for (DynamicActor actor : List.copyOf(worldState.getActors())) {
            String id = actor.getId();
            // Vehicles are positioned by metres travelled, everything else around the loop.
            double distance = actor instanceof com.one2drive.projeto1.domain.OtherVehicle
                ? worldState.vehicleSignedDistance(actor)
                : worldState.signedDistance(actor.getPositionMeters());
            // A pedestrian that has reached the sidewalk has left the road: kept in the world
            // it would still be drawn in the lane next to that sidewalk, as if in the way. The
            // one the camera is tracking stays until the camera has reported it as crossed,
            // so that the rules see the crossing end instead of the pedestrian vanishing.
            boolean leftTheRoad = actor instanceof com.one2drive.projeto1.domain.Pedestrian pedestrian
                && pedestrian.getCrossingState() == com.one2drive.projeto1.domain.PedestrianCrossingState.CROSSED
                && (!id.equals(trackedPedestrianId) || trackedPedestrianReportedCrossed);
            boolean detected = detectedWorldObjectIds.contains(id);
            if (detected && distance <= 0.0) {
                passedActorIds.add(id);
            }
            // Actors keep their own speed and follow nothing: if the car slows below a vehicle
            // it already passed, that vehicle catches up and would drive through the car from
            // behind, so it leaves the world once it closes to within one car length, before it
            // touches the car. Right after an overtake the vehicle joins the set at about -1 m,
            // inside that gap, but the car is the faster one then, so the speed test keeps it.
            boolean passed = passedActorIds.contains(id);
            boolean catchingUp = passed && distance > -CATCH_UP_GAP_METERS
                && actor.getSpeedMps() > worldState.getEgo().getSpeedMps();
            // Safety net: a passed vehicle never reappears ahead of the car.
            boolean drivingThroughCar = passed && distance > 0.0;
            if (leftTheRoad || (detected && distance < -RENDER_BEHIND_METERS) || catchingUp || drivingThroughCar) {
                worldState.removeActorById(id);
                passedActorIds.remove(id);
            }
        }
    }

    /** Emits the maneuver lifecycle entry the moment Drools' decision starts execution. */
    private void recordManeuverStart(LaneChangeState stateBefore) {
        EgoVehicleState ego = worldState.getEgo();
        if (ego.getLaneChangeState() != LaneChangeState.REQUESTED || stateBefore == LaneChangeState.REQUESTED) {
            return;
        }
        backend.getTrace().add(
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

        if (!radarUpdated) {
            radar = new SensorTelemetry.RadarView(
                radar.rangeMeters(),
                radar.targetId(),
                radar.distanceMeters(),
                radar.relativeSpeedKmh(),
                radar.ttcSeconds(),
                radar.status()
            );
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
            } else if (event instanceof PedestrianDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getPedestrianId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getPedestrianId(), "Pedestrian", e.getDistanceMeters(), "CENTER", "camera"));
            } else if (event instanceof com.one2drive.projeto1.drools.events.CrosswalkDetectedEvent e) {
                cameraUpdated = true;
                detections.removeIf(d -> d.objectId().equals(e.getCrosswalkId()));
                detections.add(new SensorTelemetry.CameraDetection(
                    e.getCrosswalkId(), "Crosswalk", e.getDistanceMeters(), "ALL", "camera"));
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
                // Events are keyed by side; a side matching the ego's own outer lane is the road edge.
                boolean roadEdge = occupancy.getLane() == worldState.getEgo().getLane()
                    && occupancy.getLane() != com.one2drive.projeto1.domain.Lane.CENTER;
                SensorTelemetry.BlindSpotView view = new SensorTelemetry.BlindSpotView(
                    config.getBlindSpotRangeMeters(),
                    occupancy.isOccupied(),
                    occupancy.isOccupied() ? "occupied" : "none",
                    occupancy.getNearestDistanceMeters(),
                    roadEdge ? "NO LANE" : occupancy.isOccupied() ? "OCCUPIED" : "CLEAR"
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
            cameraSensor.effectiveRangeMeters(worldState),
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
        backend.getTrace().add(
            simTimeMillis,
            TimelineEntryType.ACTUATOR,
            "ACTUATOR " + currentDecision.getAction() + " acceleration=" + acceleration
        );
    }

    public synchronized boolean isLaneSafe(Lane lane) {
        return worldState.isLaneSafe(lane, config.getBlindSpotRangeMeters(), 60.0);
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
        // The sensors of this tick already read the sides of the previous lane.
        ego.setLaneSettledAtMillis(simTimeMillis);
        backend.getTrace().add(
            simTimeMillis,
            TimelineEntryType.MANEUVER,
            "MANEUVER COMPLETED " + ego.getLane().name() + " lane at "
                + String.format("%.2f", ego.getLateralPositionMeters()) + "m lateral"
        );
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
        backend.getTrace().add(simTimeMillis, TimelineEntryType.WORLD_CHANGE,
            "Scenario action " + action.getType() + " at " + simTimeMillis + " ms");
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
        else if (event instanceof PedestrianDetectedEvent e) {
            objectId = e.getPedestrianId();
            trackedPedestrianId = e.getPedestrianId();
            trackedPedestrianReportedCrossed =
                e.getCrossingState() == com.one2drive.projeto1.domain.PedestrianCrossingState.CROSSED;
        }
        else if (event instanceof PotholeDetectedEvent e) objectId = e.getPotholeId();
        else if (event instanceof com.one2drive.projeto1.drools.events.CrosswalkDetectedEvent e) objectId = e.getCrosswalkId();
        else if (event instanceof BarrierDetectedEvent e) objectId = e.getBarrierId();
        else if (event instanceof ObstacleDetectedEvent e) objectId = e.getObstacleId();
        else if (event instanceof FrontDistanceEvent e) objectId = e.getTargetId();
        else if (event instanceof RelativeSpeedEvent e) objectId = e.getTargetId();
        else if (event instanceof TTCEvent e) objectId = e.getTargetId();
        if (objectId != null && !objectId.isBlank()) {
            detectedObjectTimestamps.put(objectId, simTimeMillis);
            if (!objectId.equals("none")) detectedWorldObjectIds.add(objectId);
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
        boolean hasPedestrian = events.stream().anyMatch(PedestrianDetectedEvent.class::isInstance);
        DrivingAction action = currentDecision.getSpeedAction();

        if (hasSpeedLimit && action == DrivingAction.REDUCE_SPEED) {
            demoEvidence.add("speed-limit");
        }
        if (hasReducedWeather && action == DrivingAction.REDUCE_SPEED) {
            demoEvidence.add("weather");
        }
        if (hasTtc && (action == DrivingAction.EMERGENCY_BRAKE || action == DrivingAction.BRAKE || action == DrivingAction.REDUCE_SPEED)) {
            demoEvidence.add("ttc");
        }
        if (hasPedestrian && (action == DrivingAction.BRAKE || action == DrivingAction.STOP
            || action == DrivingAction.EMERGENCY_BRAKE || action == DrivingAction.REDUCE_SPEED)) {
            demoEvidence.add("pedestrian");
        }
    }

    private void enqueueWorldMutation(Runnable mutation, String description) {
        Runnable recorded = () -> {
            mutation.run();
            backend.getTrace().add(simTimeMillis, TimelineEntryType.WORLD_CHANGE, description);
        };
        if (status == SimulationStatus.RUNNING) {
            pendingWorldMutations.add(recorded);
            return;
        }
        recorded.run();
        if (lastSessionSnapshot != null) {
            lastSessionSnapshot = buildSessionSnapshot(actuatorResult());
        }
    }

    public synchronized void addRoadElementAhead(ScenarioRoadElementDefinition definition) {
        addRoadElementAheadAndGetId(definition);
    }

    /**
     * Writes the element into the road map cell {@code positionMeters} ahead of the
     * vehicle, snapped to the block containing it.
     *
     * @return the id of the map cell the element occupies
     */
    public synchronized String addRoadElementAheadAndGetId(ScenarioRoadElementDefinition definition) {
        return writeRoadElementAhead(definition, null);
    }

    /** Resolves the cell and legend code now, so invalid input fails the request instead of a later tick. */
    private String writeRoadElementAhead(ScenarioRoadElementDefinition definition, String replacedId) {
        RoadMap map = worldState.getRoadMap();
        double position = worldState.getEgo().getPositionMeters() + definition.getPositionMeters();
        Lane lane = definition.getLane() == null ? Lane.CENTER : definition.getLane();
        int row = map.rowAt(position);
        int code = map.legend().codeFor(definition);
        enqueueWorldMutation(() -> {
            if (replacedId != null) {
                map.clear(replacedId);
            }
            map.set(row, lane, code);
        }, "Place " + definition.getType() + " in " + lane + " at " + position + " m (map cell " + RoadMap.cellId(row, lane) + ")");
        return RoadMap.cellId(row, lane);
    }

    /** Sets one road map cell to a legend code (0 frees it). */
    public synchronized void setMapCell(int row, Lane lane, int code) {
        RoadMap map = worldState.getRoadMap();
        map.checkCell(row, code);
        enqueueWorldMutation(() -> map.set(row, lane, code), "Paint road map row " + row + " lane " + lane + " with code " + code);
    }

    public synchronized void addActorAhead(ScenarioActorDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioActorDefinition d = cloneActor(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        enqueueWorldMutation(() -> worldState.addActor(ScenarioFactory.createActor(d)),
            "Place " + d.getType() + " in " + d.getLane() + " at " + d.getPositionMeters() + " m at " + d.getSpeedKmh() + " km/h");
    }

    public synchronized String addActorAheadAndGetId(ScenarioActorDefinition definition) {
        double base = worldState.getEgo().getPositionMeters();
        ScenarioActorDefinition d = cloneActor(definition);
        d.setPositionMeters(base + definition.getPositionMeters());
        DynamicActor actor = ScenarioFactory.createActor(d);
        enqueueWorldMutation(() -> worldState.addActor(actor),
            "Place " + d.getType() + " " + actor.getId() + " in " + d.getLane() + " at " + d.getPositionMeters() + " m at " + d.getSpeedKmh() + " km/h");
        return actor.getId();
    }

    /**
     * Rewrites a road element. Elements are named after their map cell, so moving one
     * gives it a new id.
     *
     * @return the id of the cell the element occupies after the update
     */
    public synchronized Optional<String> updateRoadElementById(String id, ScenarioRoadElementDefinition definition) {
        Optional<RoadElement> existing = worldState.findRoadElementById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ScenarioRoadElementDefinition update = definition.copy();
        if (update.getLane() == null) {
            update.setLane(existing.get().getLane());
        }
        double position = worldState.getEgo().getPositionMeters() + definition.getPositionMeters();
        int row = worldState.getRoadMap().rowAt(position);
        int code = worldState.getRoadMap().legend().codeFor(update);
        enqueueWorldMutation(() -> worldState.getRoadMap().updateElement(id, update, position),
            "Update road object " + id + " to " + update.getType() + " in " + update.getLane() + " at " + position + " m");
        return Optional.of(RoadMap.cellId(row, update.getLane()));
    }

    public synchronized boolean updateActorById(String id, ScenarioActorDefinition definition) {
        Optional<DynamicActor> maybeActor = worldState.findActorById(id);
        if (maybeActor.isEmpty()) {
            return false;
        }
        DynamicActor actor = maybeActor.get();
        enqueueWorldMutation(() -> applyActorUpdate(actor, definition),
            "Update actor " + id + " to lane " + definition.getLane() + " speed " + definition.getSpeedKmh() + " km/h");
        return true;
    }

    public synchronized boolean removeWorldObjectById(String id) {
        Optional<RoadElement> existingRoadElement = worldState.findRoadElementById(id);
        boolean roadExists = existingRoadElement.isPresent();
        boolean actorExists = worldState.findActorById(id).isPresent();
        if (!roadExists && !actorExists) {
            return false;
        }
        enqueueWorldMutation(() -> {
            if (roadExists) {
                worldState.removeRoadElementById(existingRoadElement.get().getId());
            }
            if (actorExists) {
                worldState.removeActorById(id);
            }
        }, "Remove world object " + id);
        return true;
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
        }, "Set rain " + rain);
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
        }, "Set visibility " + visibilityFactor + " and road grip " + gripFactor);
    }

    public synchronized void requestLaneChange(LaneChangeDirection direction) {
        backend.submitCommand(new LaneChangeRequestEvent(simTimeMillis, "ui", direction));
    }

    public synchronized void requestAcceleration(double targetSpeedKmh) {
        backend.submitCommand(new AccelerationRequestEvent(simTimeMillis, "ui", targetSpeedKmh));
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
        double legalLimit = backend.currentSpeedLimitKmh();

        List<String> timeline = new ArrayList<>();
        for (TimelineEntry entry : backend.getTrace().snapshot()) {
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
            currentDecision.getLaneAction().name(),
            currentDecision.getRiskLevel().name(),
            currentDecision.getReason(),
            currentDecision.getSourceRule(),
            Map.copyOf(lastSensorValues),
            backend.factSnapshot(),
            timeline,
            detectedObjectTimestamps.keySet().stream().sorted().toList(),
            cameraDetectedObjectTimestamps.keySet().stream().sorted().toList(),
            radarDetectedObjectTimestamps.keySet().stream().sorted().toList()
        );
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
        return backend.factViews();
    }

    public synchronized List<TimelineEntry> getTimelineEntries() {
        return backend.getTrace().snapshot();
    }

    public synchronized List<RoadRenderItem> getAllRoadItemsRelativeToEgo() {
        return roadItems(RENDER_BEHIND_METERS, Double.POSITIVE_INFINITY);
    }

    /** Matrix, legend and where the ego is on it, copied so the API never reads a cell mid-write. */
    public synchronized RoadMapSnapshot roadMapSnapshot() {
        RoadMap map = worldState.getRoadMap();
        double position = worldState.getEgo().getPositionMeters();
        return new RoadMapSnapshot(
            map.cellsCopy(),
            map.blockLengthMeters(),
            map.rowAt(position),
            map.lapAt(position),
            map.legend().entries()
        );
    }

    public synchronized WorldState getWorldState() {
        return worldState;
    }

    /** The Drools session, for Drools-specific inspection; fails while another backend decides. */
    public synchronized KnowledgeSession getKnowledgeSession() {
        if (backend instanceof DroolsBackend drools) {
            return drools.session();
        }
        throw new IllegalStateException("Drools is not the active backend: " + backendType.label() + " is");
    }

    public synchronized ExpertBackendType getBackendType() {
        return backendType;
    }

    /** Why the active backend stopped deciding, or null while it works. */
    public synchronized String getBackendError() {
        return backendError;
    }

    public synchronized DrivingDecision getCurrentDecision() {
        return currentDecision;
    }

    public synchronized ExplanationTrace getTrace() {
        return backend.getTrace();
    }

    public synchronized SessionSnapshot getLastSessionSnapshot() { return lastSessionSnapshot; }

    public synchronized void setSessionSnapshotConsumer(Consumer<SessionSnapshot> consumer) {
        sessionSnapshotConsumer = consumer == null ? snapshot -> { } : consumer;
    }

    /** Records a world edit at the exact tick when the queued mutation takes effect. */
    public synchronized void recordWorldChange(String message) {
        backend.getTrace().add(simTimeMillis, TimelineEntryType.WORLD_CHANGE, message);
    }

    public synchronized String getScenarioId() {
        return scenarioId;
    }

    public synchronized List<String> initialWorldDescription() {
        List<String> result = new ArrayList<>();
        result.add("roadMap=" + java.util.Arrays.deepToString(worldState.getRoadMap().cellsCopy()));
        worldState.getRoadElements().forEach(element -> result.add("ROAD " + element));
        worldState.getActors().forEach(actor -> result.add("ACTOR " + actor));
        result.add("WEATHER " + worldState.getWeatherState().getCondition()
            + " visibility=" + worldState.getWeatherState().getVisibilityFactor()
            + " grip=" + worldState.getWeatherState().getGripFactor());
        return List.copyOf(result);
    }

    public synchronized List<String> worldDescription() {
        List<String> result = new ArrayList<>();
        result.add("roadMap=" + java.util.Arrays.deepToString(worldState.getRoadMap().cellsCopy()));
        worldState.getRoadElements().forEach(element -> result.add("ROAD " + element));
        worldState.getActors().forEach(actor -> result.add("ACTOR " + actor));
        result.add("WEATHER " + worldState.getWeatherState().getCondition()
            + " visibility=" + worldState.getWeatherState().getVisibilityFactor()
            + " grip=" + worldState.getWeatherState().getGripFactor());
        return List.copyOf(result);
    }

    public synchronized List<com.one2drive.projeto1.api.model.ApiRoadElement> currentWorldDescription() {
        return getAllRoadItemsRelativeToEgo().stream().map(item -> new com.one2drive.projeto1.api.model.ApiRoadElement(
            item.id(), item.type(), item.positionMeters(), item.lane(), item.details(), item.worldPositionMeters())).toList();
    }

    public synchronized Set<String> getDemoEvidence() {
        return Set.copyOf(demoEvidence);
    }

    public synchronized List<String> queryCurrentDecision() {
        return backend.queryCurrentDecision();
    }

    public synchronized List<RoadRenderItem> visibleRoadItems(double forwardMeters, double backwardMeters) {
        return roadItems(backwardMeters, forwardMeters);
    }

    /**
     * World objects positioned relative to the ego. The road loops, so an object is
     * drawn ahead of the car unless it lies within {@code backwardMeters} behind it.
     * Passed elements are inactive for the sensors but keep being rendered until
     * {@link #RENDER_BEHIND_METERS}, so the UI can show them sliding behind the car.
     */
    private List<RoadRenderItem> roadItems(double backwardMeters, double forwardMeters) {
        double length = worldState.getRoadMap().lengthMeters();
        double behindWindow = Math.min(backwardMeters, length / 2);
        List<RoadRenderItem> items = new ArrayList<>();
        worldState.getRoadElements().forEach(element -> addRoadItem(items, element.getClass().getSimpleName(),
            element.getId(), element.getPositionMeters(), element.getLane(), element.toString(),
            length, behindWindow, forwardMeters));
        worldState.getActors().forEach(actor -> {
            if (actor instanceof com.one2drive.projeto1.domain.OtherVehicle) {
                // Vehicles are positioned by metres travelled, not around the loop.
                double relative = worldState.vehicleSignedDistance(actor);
                if (relative >= -behindWindow && relative <= forwardMeters) {
                    items.add(new RoadRenderItem(actor.getClass().getSimpleName(), actor.getId(), relative,
                        actor.getLane().name(), actor.toString(), actor.getPositionMeters()));
                }
                return;
            }
            addRoadItem(items, actor.getClass().getSimpleName(),
                actor.getId(), actor.getPositionMeters(), actor.getLane(), actor.toString(),
                length, behindWindow, forwardMeters);
        });
        return items;
    }

    private void addRoadItem(List<RoadRenderItem> items, String type, String id, double positionMeters, Lane lane,
                             String details, double length, double behindWindow, double forwardMeters) {
        double ahead = worldState.distanceAhead(positionMeters);
        double relative = ahead >= length - behindWindow ? ahead - length : ahead;
        if (relative >= -behindWindow && relative <= forwardMeters) {
            double worldPositionMeters = worldState.getEgo().getPositionMeters() + relative;
            items.add(new RoadRenderItem(type, id, relative, lane.name(), details, worldPositionMeters));
        }
    }

    @Override
    public synchronized void close() {
        status = SimulationStatus.STOPPED;
        pendingWorldMutations.clear();
        backend.close();
    }
}
