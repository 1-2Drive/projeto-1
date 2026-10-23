package com.one2drive.projeto1.ui;

import com.one2drive.projeto1.config.SimulationConfig;
import com.one2drive.projeto1.decision.DrivingDecision;
import com.one2drive.projeto1.domain.HazardSeverity;
import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.LaneChangeDirection;
import com.one2drive.projeto1.domain.TrafficLightState;
import com.one2drive.projeto1.scenario.ScenarioActorDefinition;
import com.one2drive.projeto1.scenario.ScenarioActorType;
import com.one2drive.projeto1.scenario.ScenarioDefinition;
import com.one2drive.projeto1.scenario.ScenarioLibrary;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import com.one2drive.projeto1.simulation.RoadRenderItem;
import com.one2drive.projeto1.simulation.SimulationEngine;
import com.one2drive.projeto1.simulation.SimulationSnapshot;
import com.one2drive.projeto1.simulation.SimulationStatus;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DrivingExpertSystemApp extends Application {
    private static final double ROAD_VIEW_FORWARD = 220;
    private static final double ROAD_VIEW_BACKWARD = 40;

    private final SimulationConfig config = SimulationConfig.defaultConfig();
    private ScenarioLibrary scenarioLibrary;
    private SimulationEngine engine;
    private ScheduledExecutorService scheduler;

    private final Label statusLabel = new Label("STOPPED");
    private final Label timeLabel = new Label("0.0 s");
    private final Label speedLabel = new Label("0 km/h");
    private final Label laneLabel = new Label("CENTER");
    private final Label positionLabel = new Label("0.0 m");
    private final Label legalLimitLabel = new Label("120 km/h");
    private final Label recommendedLabel = new Label("90 km/h");
    private final Label decisionLabel = new Label("MAINTAIN_SPEED");
    private final Label reasonLabel = new Label("No restrictive condition detected");
    private final Label ruleLabel = new Label("-");

    private final ListView<String> sensorList = new ListView<>();
    private final ListView<String> timelineList = new ListView<>();
    private final ListView<String> workingMemoryList = new ListView<>();
    private final TextArea explanationArea = new TextArea();
    private final Canvas roadCanvas = new Canvas(760, 320);

    private final ComboBox<String> scenarioSelector = new ComboBox<>();
    private final ComboBox<String> laneSelector = new ComboBox<>();
    private final ComboBox<String> distanceSelector = new ComboBox<>();

    @Override
    public void start(Stage stage) {
        scenarioLibrary = ScenarioLibrary.loadFromClasspath("/scenarios/scenarios.json");
        scenarioLibrary.getScenarios().forEach(s -> scenarioSelector.getItems().add(s.getId() + " - " + s.getName()));
        scenarioSelector.getSelectionModel().selectFirst();

        laneSelector.getItems().addAll("LEFT", "CENTER", "RIGHT");
        laneSelector.getSelectionModel().select("CENTER");
        distanceSelector.getItems().addAll("40", "80", "120", "180");
        distanceSelector.getSelectionModel().select("80");

        engine = createEngine(getSelectedScenario());

        BorderPane root = new BorderPane();
        root.setBackground(new Background(new BackgroundFill(Color.web("#f4f6fb"), CornerRadii.EMPTY, Insets.EMPTY)));
        root.setTop(buildHeader());
        root.setLeft(buildToolbox());
        root.setCenter(buildRoadCenter());
        root.setRight(buildStatusPanel());
        root.setBottom(buildBottomPanel());

        Scene scene = new Scene(root, 1600, 920);
        stage.setTitle("Sistema Inteligente de Apoio à Decisao de Conducao - Java + Drools");
        stage.setScene(scene);
        stage.show();

        startScheduler();
        refreshUi(engine.snapshot());
    }

    private SimulationEngine createEngine(ScenarioDefinition scenarioDefinition) {
        return new SimulationEngine(config, scenarioDefinition, false, this::onDecisionLiveUpdate);
    }

    private void onDecisionLiveUpdate(DrivingDecision decision) {
        Platform.runLater(() -> explanationArea.setText(buildExplanation(decision)));
    }

    private VBox buildHeader() {
        Label title = new Label("Sistema Inteligente de Apoio a Decisao de Conducao");
        title.setFont(Font.font("Georgia", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#1f2a44"));

        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);
        Button start = actionButton("START", () -> engine.start());
        Button pause = actionButton("PAUSE", () -> engine.pause());
        Button resume = actionButton("RESUME", () -> engine.start());
        Button stop = actionButton("STOP", () -> engine.stop());
        Button reset = actionButton("RESET", this::resetScenario);
        controls.getChildren().addAll(start, pause, resume, stop, reset, new Separator(), new Label("Scenario:"), scenarioSelector);

        HBox status = new HBox(24,
            new Label("Status:"), statusLabel,
            new Label("Simulation Time:"), timeLabel
        );
        status.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(8, title, controls, status);
        top.setPadding(new Insets(16, 20, 12, 20));
        top.setBackground(new Background(new BackgroundFill(Color.web("#dbe6ff"), CornerRadii.EMPTY, Insets.EMPTY)));
        return top;
    }

    private VBox buildToolbox() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.setPrefWidth(280);
        box.setBackground(new Background(new BackgroundFill(Color.web("#f8fafc"), CornerRadii.EMPTY, Insets.EMPTY)));

        Label title = sectionTitle("SCENARIO TOOLBOX");
        Label laneLabelUi = new Label("Lane");
        Label distanceLabelUi = new Label("Distance Ahead (m)");

        GridPane baseControls = new GridPane();
        baseControls.setHgap(6);
        baseControls.setVgap(6);
        baseControls.add(laneLabelUi, 0, 0);
        baseControls.add(laneSelector, 0, 1);
        baseControls.add(distanceLabelUi, 0, 2);
        baseControls.add(distanceSelector, 0, 3);

        VBox signs = group("Signs",
            addRoadElementButton("Speed 30", speedLimit(30)),
            addRoadElementButton("Speed 50", speedLimit(50)),
            addRoadElementButton("Speed 70", speedLimit(70)),
            addRoadElementButton("Speed 90", speedLimit(90)),
            addRoadElementButton("Speed 120", speedLimit(120)),
            addRoadElementButton("STOP", stopSign())
        );

        VBox road = group("Road",
            addRoadElementButton("Pothole", pothole(HazardSeverity.HIGH)),
            addRoadElementButton("Barrier Closed", barrier(false)),
            addRoadElementButton("Barrier Open", barrier(true)),
            addRoadElementButton("Traffic Light Red", trafficLight(TrafficLightState.RED)),
            addRoadElementButton("Traffic Light Green", trafficLight(TrafficLightState.GREEN))
        );

        VBox actors = group("Actors",
            addActorButton("Vehicle Ahead", actor(ScenarioActorType.FRONT_VEHICLE, 50)),
            addActorButton("Vehicle Left", actor(ScenarioActorType.VEHICLE_LEFT, 60)),
            addActorButton("Vehicle Right", actor(ScenarioActorType.VEHICLE_RIGHT, 60)),
            addActorButton("Pedestrian", actor(ScenarioActorType.PEDESTRIAN, 5))
        );

        VBox environment = group("Environment",
            actionButton("Rain", () -> engine.setRain(true)),
            actionButton("Clear", () -> engine.setRain(false)),
            actionButton("Low Visibility", () -> engine.setWeatherFactors(0.55, engine.getWorldState().getWeatherState().getGripFactor())),
            actionButton("Low Grip", () -> engine.setWeatherFactors(engine.getWorldState().getWeatherState().getVisibilityFactor(), 0.55)),
            actionButton("Lane Change Left", () -> engine.requestLaneChange(LaneChangeDirection.LEFT)),
            actionButton("Lane Change Right", () -> engine.requestLaneChange(LaneChangeDirection.RIGHT)),
            actionButton("Request Accel 90", () -> engine.requestAcceleration(90.0))
        );

        box.getChildren().addAll(title, baseControls, signs, road, actors, environment);
        return box;
    }

    private VBox buildRoadCenter() {
        VBox center = new VBox(8);
        center.setPadding(new Insets(14));
        center.setBackground(new Background(new BackgroundFill(Color.web("#eaf0ff"), CornerRadii.EMPTY, Insets.EMPTY)));
        Label title = sectionTitle("ROAD SIMULATION");
        center.getChildren().addAll(title, roadCanvas);
        VBox.setVgrow(roadCanvas, Priority.ALWAYS);
        return center;
    }

    private VBox buildStatusPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(380);
        panel.setBackground(new Background(new BackgroundFill(Color.web("#f7f9fd"), CornerRadii.EMPTY, Insets.EMPTY)));

        panel.getChildren().addAll(
            sectionTitle("VEHICLE"),
            line("Speed", speedLabel),
            line("Lane", laneLabel),
            line("Position", positionLabel),
            new Separator(),
            sectionTitle("SPEED ADVISOR"),
            line("Current", speedLabel),
            line("Legal Limit", legalLimitLabel),
            line("Recommended", recommendedLabel),
            new Separator(),
            sectionTitle("SENSORS"),
            sensorList,
            new Separator(),
            sectionTitle("CURRENT DECISION"),
            decisionLabel,
            line("Why", reasonLabel),
            line("Rule", ruleLabel),
            new Separator(),
            sectionTitle("EXPLANATION"),
            explanationArea,
            new Separator(),
            sectionTitle("WORKING MEMORY"),
            workingMemoryList
        );

        sensorList.setPrefHeight(160);
        explanationArea.setPrefHeight(120);
        explanationArea.setWrapText(true);
        workingMemoryList.setPrefHeight(170);
        return panel;
    }

    private VBox buildBottomPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(12, 20, 12, 20));
        panel.setBackground(new Background(new BackgroundFill(Color.web("#dfe7ff"), CornerRadii.EMPTY, Insets.EMPTY)));
        panel.getChildren().addAll(sectionTitle("TIMELINE"), timelineList);
        timelineList.setPrefHeight(160);
        return panel;
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "simulation-ui-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            SimulationSnapshot snapshot = engine.tick();
            Platform.runLater(() -> refreshUi(snapshot));
        }, 0, config.getTickMillis(), TimeUnit.MILLISECONDS);
    }

    private void refreshUi(SimulationSnapshot snapshot) {
        statusLabel.setText(snapshot.status().name());
        timeLabel.setText(String.format("%.1f s", snapshot.simTimeMillis() / 1000.0));
        speedLabel.setText(String.format("%.1f km/h", snapshot.egoSpeedKmh()));
        laneLabel.setText(snapshot.egoLane());
        positionLabel.setText(String.format("%.1f m", snapshot.egoPositionMeters()));
        legalLimitLabel.setText(String.format("%.0f km/h", snapshot.legalLimitKmh()));
        recommendedLabel.setText(String.format("%.1f km/h", snapshot.recommendedSpeedKmh()));
        decisionLabel.setText(snapshot.currentDecision());
        reasonLabel.setText(snapshot.decisionReason());
        ruleLabel.setText(snapshot.sourceRule());

        List<String> sensorValues = new ArrayList<>();
        for (Map.Entry<String, String> entry : snapshot.sensorValues().entrySet()) {
            sensorValues.add(entry.getKey() + " => " + entry.getValue());
        }
        sensorValues.sort(String::compareTo);
        sensorList.getItems().setAll(sensorValues);

        timelineList.getItems().setAll(snapshot.timeline());
        workingMemoryList.getItems().setAll(snapshot.workingMemoryFacts());
        explanationArea.setText(buildExplanation(engine.getCurrentDecision()));

        renderRoad(engine.visibleRoadItems(ROAD_VIEW_FORWARD, ROAD_VIEW_BACKWARD), snapshot.egoLane());
    }

    private void renderRoad(List<RoadRenderItem> items, String egoLane) {
        GraphicsContext gc = roadCanvas.getGraphicsContext2D();
        double width = roadCanvas.getWidth();
        double height = roadCanvas.getHeight();

        gc.setFill(Color.web("#cfd8e6"));
        gc.fillRect(0, 0, width, height);

        gc.setFill(Color.web("#4b5563"));
        gc.fillRect(40, 30, width - 80, height - 60);

        gc.setStroke(Color.web("#f8fafc"));
        gc.setLineWidth(2.0);
        double laneY1 = laneToY(Lane.LEFT);
        double laneY2 = laneToY(Lane.CENTER);
        double laneY3 = laneToY(Lane.RIGHT);
        gc.strokeLine(40, (laneY1 + laneY2) / 2, width - 40, (laneY1 + laneY2) / 2);
        gc.strokeLine(40, (laneY2 + laneY3) / 2, width - 40, (laneY2 + laneY3) / 2);

        double egoX = toX(0);
        gc.setFill(Color.web("#f59e0b"));
        gc.fillRoundRect(egoX - 26, laneToY(Lane.valueOf(egoLane)) - 12, 52, 24, 8, 8);
        gc.setFill(Color.BLACK);
        gc.fillText("EGO", egoX - 10, laneToY(Lane.valueOf(egoLane)) + 4);

        for (RoadRenderItem item : items) {
            if (Math.abs(item.positionMeters()) < 1) {
                continue;
            }
            Lane lane = Lane.valueOf(item.lane());
            double x = toX(item.positionMeters());
            double y = laneToY(lane);
            if (item.type().contains("Vehicle")) {
                gc.setFill(Color.web("#06b6d4"));
                gc.fillRoundRect(x - 18, y - 10, 36, 20, 6, 6);
            } else if (item.type().contains("Pedestrian")) {
                gc.setFill(Color.web("#ef4444"));
                gc.fillOval(x - 8, y - 8, 16, 16);
            } else {
                gc.setFill(Color.web("#22c55e"));
                gc.fillRect(x - 8, y - 8, 16, 16);
            }
            gc.setFill(Color.WHITE);
            gc.fillText(shortName(item.type()), x - 14, y - 12);
        }
    }

    private double toX(double relativeMeters) {
        double width = roadCanvas.getWidth() - 80;
        double normalized = (relativeMeters + ROAD_VIEW_BACKWARD) / (ROAD_VIEW_FORWARD + ROAD_VIEW_BACKWARD);
        normalized = Math.min(1.0, Math.max(0.0, normalized));
        return 40 + normalized * width;
    }

    private double laneToY(Lane lane) {
        return switch (lane) {
            case LEFT -> 95;
            case CENTER -> 160;
            case RIGHT -> 225;
            case ALL -> 40;
        };
    }

    private String shortName(String type) {
        if (type.contains("SpeedLimit")) {
            return "SL";
        }
        if (type.contains("Stop")) {
            return "STOP";
        }
        if (type.contains("TrafficLight")) {
            return "TL";
        }
        if (type.contains("Pothole")) {
            return "PH";
        }
        if (type.contains("Barrier")) {
            return "BR";
        }
        if (type.contains("Vehicle")) {
            return "CAR";
        }
        if (type.contains("Pedestrian")) {
            return "PED";
        }
        return type.substring(0, Math.min(3, type.length())).toUpperCase();
    }

    private void resetScenario() {
        ScenarioDefinition selected = getSelectedScenario();
        engine.reset(selected);
        refreshUi(engine.snapshot());
    }

    private ScenarioDefinition getSelectedScenario() {
        String value = scenarioSelector.getValue();
        String id = value.split(" - ")[0];
        return scenarioLibrary.findById(id).orElseThrow();
    }

    private String buildExplanation(DrivingDecision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append("Decision: ").append(decision.getAction()).append('\n');
        sb.append("Rule: ").append(decision.getSourceRule()).append('\n');
        sb.append("Reason: ").append(decision.getReason()).append('\n');
        sb.append("Evidence:");
        decision.getEvidence().forEach(e -> sb.append("\n - ").append(e));
        return sb.toString();
    }

    private Button addRoadElementButton(String text, ScenarioRoadElementDefinition def) {
        return actionButton(text, () -> engine.addRoadElementAhead(adjustRoadDefinition(def)));
    }

    private Button addActorButton(String text, ScenarioActorDefinition def) {
        return actionButton(text, () -> engine.addActorAhead(adjustActorDefinition(def)));
    }

    private ScenarioRoadElementDefinition adjustRoadDefinition(ScenarioRoadElementDefinition def) {
        ScenarioRoadElementDefinition copy = new ScenarioRoadElementDefinition();
        copy.setType(def.getType());
        copy.setLane(Lane.valueOf(laneSelector.getValue()));
        copy.setPositionMeters(Double.parseDouble(distanceSelector.getValue()));
        copy.setSpeedLimitKmh(def.getSpeedLimitKmh());
        copy.setTrafficLightState(def.getTrafficLightState());
        copy.setSeverity(def.getSeverity());
        copy.setBarrierOpen(def.getBarrierOpen());
        return copy;
    }

    private ScenarioActorDefinition adjustActorDefinition(ScenarioActorDefinition def) {
        ScenarioActorDefinition copy = new ScenarioActorDefinition();
        copy.setType(def.getType());
        copy.setLane(resolveActorLane(def.getType()));
        copy.setPositionMeters(Double.parseDouble(distanceSelector.getValue()));
        copy.setSpeedKmh(def.getSpeedKmh());
        return copy;
    }

    private Lane resolveActorLane(ScenarioActorType type) {
        if (type == ScenarioActorType.VEHICLE_LEFT) {
            return Lane.LEFT;
        }
        if (type == ScenarioActorType.VEHICLE_RIGHT) {
            return Lane.RIGHT;
        }
        return Lane.valueOf(laneSelector.getValue());
    }

    private ScenarioRoadElementDefinition speedLimit(int limit) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(ScenarioRoadElementType.SPEED_LIMIT);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(80);
        d.setSpeedLimitKmh(limit);
        return d;
    }

    private ScenarioRoadElementDefinition stopSign() {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(ScenarioRoadElementType.STOP_SIGN);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(80);
        return d;
    }

    private ScenarioRoadElementDefinition pothole(HazardSeverity severity) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(ScenarioRoadElementType.POTHOLE);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(60);
        d.setSeverity(severity);
        return d;
    }

    private ScenarioRoadElementDefinition barrier(boolean open) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(ScenarioRoadElementType.BARRIER);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(80);
        d.setBarrierOpen(open);
        return d;
    }

    private ScenarioRoadElementDefinition trafficLight(TrafficLightState state) {
        ScenarioRoadElementDefinition d = new ScenarioRoadElementDefinition();
        d.setType(ScenarioRoadElementType.TRAFFIC_LIGHT);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(80);
        d.setTrafficLightState(state);
        return d;
    }

    private ScenarioActorDefinition actor(ScenarioActorType type, double speedKmh) {
        ScenarioActorDefinition d = new ScenarioActorDefinition();
        d.setType(type);
        d.setLane(Lane.CENTER);
        d.setPositionMeters(80);
        d.setSpeedKmh(speedKmh);
        return d;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 13));
        label.setTextFill(Color.web("#243b5a"));
        return label;
    }

    private HBox line(String title, Label value) {
        Label key = new Label(title + ":");
        key.setMinWidth(96);
        return new HBox(8, key, value);
    }

    private VBox group(String title, Button... buttons) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionTitle(title));
        box.getChildren().addAll(buttons);
        box.setPadding(new Insets(8));
        box.setBackground(new Background(new BackgroundFill(Color.web("#eef3ff"), new CornerRadii(6), Insets.EMPTY)));
        return box;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (engine != null) {
            engine.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
