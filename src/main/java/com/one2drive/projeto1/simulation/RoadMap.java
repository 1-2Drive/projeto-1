package com.one2drive.projeto1.simulation;

import com.one2drive.projeto1.domain.Lane;
import com.one2drive.projeto1.domain.RoadElement;
import com.one2drive.projeto1.scenario.ScenarioFactory;
import com.one2drive.projeto1.scenario.ScenarioRoadElementType;
import com.one2drive.projeto1.scenario.ScenarioRoadElementDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The road as a matrix: one row per block along the road and one column per lane
 * (LEFT, CENTER, RIGHT). Each cell holds a {@link MapLegend} code, 0 meaning free.
 *
 * <p>The road is a loop: driving past the last row continues at row 0. Positions stay
 * as metres travelled, so every distance on the map is measured modulo
 * {@link #lengthMeters()}. Each occupied cell is backed by one road element placed at
 * the start of its block and named after the cell ({@code r12-CENTER}), so sensors and
 * rules keep working with ordinary road elements.
 */
public class RoadMap {
    private static final Lane[] LANES = {Lane.LEFT, Lane.CENTER, Lane.RIGHT};

    private final int[][] cells;
    private final RoadElement[][] elements;
    private final double blockLengthMeters;
    private final MapLegend legend;

    public RoadMap(int[][] cells, double blockLengthMeters, MapLegend legend) {
        if (cells == null || cells.length == 0) {
            throw new IllegalArgumentException("The road map needs at least one row");
        }
        if (blockLengthMeters <= 0) {
            throw new IllegalArgumentException("Road map blocks must be longer than 0 m");
        }
        this.blockLengthMeters = blockLengthMeters;
        this.legend = legend;
        this.cells = new int[cells.length][LANES.length];
        this.elements = new RoadElement[cells.length][LANES.length];
        for (int row = 0; row < cells.length; row++) {
            if (cells[row] == null || cells[row].length != LANES.length) {
                throw new IllegalArgumentException("Road map row " + row + " must have "
                    + LANES.length + " values (LEFT, CENTER, RIGHT)");
            }
            int radarCode = 0;
            for (Lane lane : LANES) {
                int code = cells[row][lane.ordinal()];
                if (code != MapLegend.FREE && legend.entry(code).isPresent()
                    && legend.entry(code).orElseThrow().template().getType() == ScenarioRoadElementType.SPEED_RADAR) {
                    radarCode = code;
                    break;
                }
            }
            if (radarCode != 0) {
                set(row, Lane.CENTER, radarCode);
            } else {
                for (Lane lane : LANES) {
                    set(row, lane, cells[row][lane.ordinal()]);
                }
            }
        }
    }

    public static RoadMap empty(int rows, double blockLengthMeters, MapLegend legend) {
        return new RoadMap(new int[rows][LANES.length], blockLengthMeters, legend);
    }

    public int rows() {
        return cells.length;
    }

    public double blockLengthMeters() {
        return blockLengthMeters;
    }

    public double lengthMeters() {
        return cells.length * blockLengthMeters;
    }

    public MapLegend legend() {
        return legend;
    }

    public int get(int row, Lane lane) {
        return cells[checkRow(row)][lane.ordinal()];
    }

    /** Writes a legend code into a cell and rebuilds the road element it stands for. */
    public void set(int row, Lane lane, int code) {
        checkCell(row, code);
        int checkedRow = checkRow(row);
        int previousCode = cells[checkedRow][lane.ordinal()];
        if (code == MapLegend.FREE && previousCode != MapLegend.FREE
            && legend.entry(previousCode).orElseThrow().template().getType() == ScenarioRoadElementType.SPEED_RADAR) {
            for (Lane roadLane : LANES) {
                cells[checkedRow][roadLane.ordinal()] = MapLegend.FREE;
                elements[checkedRow][roadLane.ordinal()] = null;
            }
            return;
        }
        if (code != MapLegend.FREE
            && legend.entry(code).orElseThrow().template().getType() == ScenarioRoadElementType.SPEED_RADAR) {
            int radarRow = checkedRow;
            ScenarioRoadElementDefinition definition = legend.entry(code).orElseThrow().template().copy();
            definition.setLane(Lane.CENTER);
            definition.setPositionMeters(radarRow * blockLengthMeters);
            RoadElement radar = ScenarioFactory.createRoadElement(definition);
            radar.setId("r" + radarRow + "-ALL");
            for (Lane roadLane : LANES) {
                cells[radarRow][roadLane.ordinal()] = code;
                elements[radarRow][roadLane.ordinal()] = roadLane == Lane.LEFT ? radar : null;
            }
            return;
        }
        cells[row][lane.ordinal()] = code;
        elements[row][lane.ordinal()] = code == MapLegend.FREE
            ? null
            : createElement(row, lane, legend.entry(code).orElseThrow());
    }

    /**
     * Writes an element definition at an absolute position, snapped to the block that
     * contains it.
     *
     * @return the id of the cell it now occupies
     */
    public String place(ScenarioRoadElementDefinition definition, double positionMeters) {
        Lane lane = definition.getLane() == null ? Lane.CENTER : definition.getLane();
        int row = rowAt(positionMeters);
        int code = legend.codeFor(definition);
        if (definition.getType() == ScenarioRoadElementType.SPEED_RADAR) {
            set(row, Lane.CENTER, code);
            return "r" + row + "-ALL";
        }
        set(row, lane, code);
        return cellId(row, lane);
    }

    public Optional<String> findCellIdByElementId(String elementId) {
        for (int row = 0; row < cells.length; row++) {
            for (Lane lane : LANES) {
                RoadElement element = elements[row][lane.ordinal()];
                if (element != null && element.getId().equals(elementId)) {
                    return Optional.of(element instanceof com.one2drive.projeto1.domain.SpeedRadar
                        ? "r" + row + "-ALL" : cellId(row, lane));
                }
            }
        }
        return Optional.empty();
    }

    /** Frees the cell backing the given element id. */
    public boolean clear(String elementId) {
        for (int row = 0; row < cells.length; row++) {
            for (Lane lane : LANES) {
                RoadElement element = elements[row][lane.ordinal()];
                if (element != null && element.getId().equals(elementId)) {
                    if (element instanceof com.one2drive.projeto1.domain.SpeedRadar) {
                        for (Lane radarLane : LANES) {
                            cells[row][radarLane.ordinal()] = MapLegend.FREE;
                            elements[row][radarLane.ordinal()] = null;
                        }
                        return true;
                    }
                    set(row, lane, MapLegend.FREE);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean updateElement(String elementId, ScenarioRoadElementDefinition definition, double positionMeters) {
        Optional<String> cellId = findCellIdByElementId(elementId);
        if (cellId.isEmpty()) return false;
        RoadElement existing = findElement(cellId.get()).orElse(null);
        if (existing == null) return false;
        Lane lane = definition.getType() == ScenarioRoadElementType.SPEED_RADAR ? Lane.CENTER
            : (definition.getLane() == null ? existing.getLane() : definition.getLane());
        int newRow = rowAt(positionMeters);
        int code = legend.codeFor(definition);
        clear(elementId);
        if (definition.getType() == ScenarioRoadElementType.SPEED_RADAR) {
            set(newRow, lane, code);
            return true;
        }
        set(newRow, lane, code);
        RoadElement replacement = elements[newRow][lane.ordinal()];
        if (replacement != null) replacement.setId(elementId);
        return true;
    }

    public Optional<RoadElement> findElement(String elementId) {
        return elements().stream().filter(e -> e.getId().equals(elementId)).findFirst();
    }

    /** Road elements of every occupied cell, in row order. */
    public List<RoadElement> elements() {
        List<RoadElement> result = new ArrayList<>();
        for (RoadElement[] row : elements) {
            for (RoadElement element : row) {
                if (element != null && (!(element instanceof com.one2drive.projeto1.domain.SpeedRadar)
                    || result.stream().noneMatch(existing -> existing.getId().equals(element.getId())))) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    public int[][] cellsCopy() {
        int[][] copy = new int[cells.length][];
        for (int row = 0; row < cells.length; row++) {
            copy[row] = cells[row].clone();
        }
        return copy;
    }

    /** Applies a legend code at a longitudinal row; speed enforcement spans all lanes. */
    public void setInfrastructureRow(int row, int code) {
        checkCell(row, code);
        for (Lane lane : LANES) set(row, lane, code);
    }

    public void clearInfrastructureRow(int row) {
        int checkedRow = checkRow(row);
        for (Lane lane : LANES) {
            cells[checkedRow][lane.ordinal()] = MapLegend.FREE;
            elements[checkedRow][lane.ordinal()] = null;
        }
    }

    /** Row of the block containing the given position. */
    public int rowAt(double positionMeters) {
        return Math.min(rows() - 1, (int) Math.floor(wrap(positionMeters) / blockLengthMeters));
    }

    /** Completed laps at the given position (0 on the first pass). */
    public long lapAt(double positionMeters) {
        return (long) Math.floor(positionMeters / lengthMeters());
    }

    /** Distance driving forward from one position to the next occurrence of another, in [0, length). */
    public double distanceAhead(double fromMeters, double toMeters) {
        return wrap(toMeters - fromMeters);
    }

    /** Shortest signed distance around the loop, in [-length/2, length/2). Negative is behind. */
    public double signedDistance(double fromMeters, double toMeters) {
        double ahead = distanceAhead(fromMeters, toMeters);
        return ahead >= lengthMeters() / 2 ? ahead - lengthMeters() : ahead;
    }

    public static String cellId(int row, Lane lane) {
        return "r" + row + "-" + lane.name();
    }

    private RoadElement createElement(int row, Lane lane, MapLegend.Entry entry) {
        ScenarioRoadElementDefinition definition = entry.template().copy();
        if (definition.getType() == ScenarioRoadElementType.SPEED_RADAR) {
            if (lane != Lane.LEFT) return null;
            definition.setLane(Lane.CENTER);
        } else {
            definition.setLane(lane);
        }
        definition.setPositionMeters(row * blockLengthMeters);
        RoadElement element = ScenarioFactory.createRoadElement(definition);
        element.setId(definition.getType() == ScenarioRoadElementType.SPEED_RADAR
            ? "r" + row + "-ALL" : cellId(row, lane));
        return element;
    }

    /** Rejects a row outside the map or a code the legend does not define. */
    public void checkCell(int row, int code) {
        checkRow(row);
        if (code != MapLegend.FREE && legend.entry(code).isEmpty()) {
            throw new IllegalArgumentException("Unknown road map code " + code + " at row " + row);
        }
    }

    private int checkRow(int row) {
        if (row < 0 || row >= cells.length) {
            throw new IllegalArgumentException("Road map row " + row + " is outside 0.." + (cells.length - 1));
        }
        return row;
    }

    private double wrap(double meters) {
        double length = lengthMeters();
        double wrapped = meters - length * Math.floor(meters / length);
        return wrapped >= length ? 0.0 : wrapped;
    }
}
