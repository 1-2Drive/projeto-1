export type SimulationStatus = 'RUNNING' | 'PAUSED' | 'STOPPED';
export type StreamStatus = 'LIVE' | 'RECONNECTING' | 'DISCONNECTED';
export type DemoPhaseStatus = 'DONE' | 'ACTIVE' | 'PENDING';

export interface ScenarioSummary {
  id: string;
  name: string;
  initialSpeedKmh: number;
  initialWeather: string;
  roadElementCount: number;
  actorCount: number;
  timedActionCount: number;
}

export interface CommandResponse {
  success: boolean;
  message: string;
}

export interface WorldMutationResponse {
  success: boolean;
  objectId: string;
  message: string;
}

export interface ApiRoadElement {
  id: string;
  type: string;
  positionMeters: number;
  lane: 'LEFT' | 'CENTER' | 'RIGHT' | 'ALL';
  details: string;
  worldPositionMeters?: number;
}

export interface ApiWorkingMemoryRow {
  type: string;
  value: string;
  source: string;
  timestamp: number | null;
  stream: string;
}

export interface ApiTimelineEntry {
  timestamp: number;
  type: string;
  message: string;
}

export interface ApiDecisionView {
  action: string;
  recommendedSpeedKmh: number;
  riskLevel: string;
  reason: string;
  sourceRule: string;
  timestamp: number;
  evidence: string[];
}

export interface ApiSimulationSnapshot {
  simulationTimeMillis: number;
  status: SimulationStatus;
  egoVehicle: {
    speedKmh: number;
    positionMeters: number;
    lane: LaneName;
    accelerationMps2: number;
    /** Authoritative lateral coordinate the road view renders. */
    lateralPositionMeters: number;
    targetLane: LaneName;
    laneChangeState: LaneChangeStateName;
    laneChangeProgress: number;
    drivingState?: string;
    indicatorState?: string;
    steeringState?: string;
    headingDegrees?: number;
  };
  environment: {
    weatherCondition: string;
    visibilityFactor: number;
    gripFactor: number;
  };
  speedAdvisor: {
    currentKmh: number;
    legalLimitKmh: number;
    recommendedKmh: number;
    action: string;
  };
  currentDecision: ApiDecisionView;
  sensorReadings: Record<string, string>;
  worldObjects: ApiRoadElement[];
  workingMemory: ApiWorkingMemoryRow[];
  recentTimeline: ApiTimelineEntry[];
  recentRules: string[];
  recentFacts: string[];
  activeStage: string;
  tickMillis: number;
  detectedObjectIds: string[];
  cameraDetectedObjectIds: string[];
  radarDetectedObjectIds: string[];
  sensorRanges: {
    cameraRangeMeters: number;
    radarRangeMeters: number;
    blindSpotRangeMeters: number;
    cameraRateMillis: number;
    radarRateMillis: number;
  };
  demoPhases: { id: string; label: string; status: DemoPhaseStatus }[];
  maneuver?: ApiManeuverView;
  sensors?: ApiSensorTelemetryView;
  inference?: ApiInferenceSummaryView;
  roadMap?: ApiRoadMapView;
  roadMarks?: {
    leftMarking: 'DASHED' | 'CONTINUOUS' | string;
    rightMarking: 'DASHED' | 'CONTINUOUS' | string;
  };
  laneSafety?: Array<{ lane: LaneName; safe: boolean }>;
}

/**
 * The road as a matrix: cells[row][lane], rows going forward along a looping road and
 * lanes in the `lanes` order. 0 is a free cell, any other number is a legend code.
 */
export interface ApiRoadMapView {
  rows: number;
  lanes: LaneName[];
  blockLengthMeters: number;
  egoRow: number;
  lap: number;
  cells: number[][];
  legend: ApiMapLegendEntry[];
}

/** Meaning of one map code; detail is the limit, light colour, severity or barrier state. */
export interface ApiMapLegendEntry {
  code: number;
  type: string;
  label: string;
  detail: string;
}

export interface MapCellRequest {
  row: number;
  lane: LaneName;
  code: number;
}

export type LaneName = 'LEFT' | 'CENTER' | 'RIGHT';
export type LaneChangeStateName = 'IDLE' | 'REQUESTED' | 'CHANGING' | 'COMPLETED';

/** Physical execution state of the maneuver Drools selected. */
export interface ApiManeuverView {
  state: LaneChangeStateName;
  fromLane: LaneName;
  targetLane: LaneName;
  progress: number;
  lateralPositionMeters: number;
  indicatorsLeft: boolean;
  indicatorsRight: boolean;
  braking: boolean;
  steering?: string;
}

/** NO LANE: a blind-spot side past the road edge. */
export type SensorStatus = 'CLEAR' | 'TRACKING' | 'DETECTED' | 'WARNING' | 'CRITICAL' | 'OCCUPIED' | 'NO LANE';

export interface ApiRadarTelemetryView {
  rangeMeters: number;
  targetId: string;
  distanceMeters: number;
  relativeSpeedKmh: number;
  ttcSeconds: number;
  status: SensorStatus;
  speedRadarDistanceMeters?: number;
  speedRadarLimitKmh?: number;
}

export interface ApiCameraDetectionView {
  objectId: string;
  label: string;
  distanceMeters: number;
  lane: string;
}

export interface ApiCameraTelemetryView {
  rangeMeters: number;
  detections: ApiCameraDetectionView[];
  status: SensorStatus;
}

export interface ApiBlindSpotTelemetryView {
  rangeMeters: number;
  occupied: boolean;
  targetId: string;
  distanceMeters: number;
  status: SensorStatus;
}

export interface ApiEnvironmentTelemetryView {
  condition: string;
  effectiveRangeMeters: number;
  gripPercent: number;
  rain: boolean;
}

export interface ApiSensorTelemetryView {
  frontRadar: ApiRadarTelemetryView;
  frontCamera: ApiCameraTelemetryView;
  blindSpotLeft: ApiBlindSpotTelemetryView;
  blindSpotRight: ApiBlindSpotTelemetryView;
  environment: ApiEnvironmentTelemetryView;
}

/**
 * Presentation summary of the latest inference cycle, produced by the backend from
 * evidence the engine and Drools already emitted. Angular formats it; it never
 * derives conclusions from sensor values.
 */
export interface ApiInferenceSummaryView {
  previousDecision: string | null;
  decisionChangedAtMillis: number;
  situationSteps: string[];
  lastImportantEvent: ApiImportantEventView | null;
  activeRule: string | null;
}

export interface ApiImportantEventView {
  timestampMillis: number;
  category: 'PERCEPTION' | 'MANEUVER' | 'DECISION' | 'RULE' | 'INFO';
  title: string;
  detail: string;
}

export interface ApiSystemStatus {
  backend: string;
  drools: string;
  simulation: string;
}

export interface SnapshotStreamMessage {
  event: 'snapshot';
  payload: ApiSimulationSnapshot;
}

export interface CreateRoadElementRequest {
  type: string;
  lane: string;
  distanceAheadMeters: number;
  speedLimitKmh?: number;
  trafficLightState?: string;
  severity?: string;
  barrierOpen?: boolean;
}

export interface CreateActorRequest {
  type: string;
  lane: string;
  distanceAheadMeters: number;
  speedKmh: number;
}

export interface EnvironmentUpdateRequest {
  visibilityFactor?: number;
  gripFactor?: number;
  rain?: boolean;
}

export interface UiStatus {
  backendConnected: boolean;
  droolsActive: boolean;
  simulationStatus: SimulationStatus;
  streamStatus: StreamStatus;
}

export interface UiModel {
  snapshot: ApiSimulationSnapshot | null;
  scenarios: ScenarioSummary[];
  connection: UiStatus;
  error: string | null;
  loading: boolean;
}
