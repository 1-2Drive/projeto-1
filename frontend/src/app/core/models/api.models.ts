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
  lane: 'LEFT' | 'CENTER' | 'RIGHT';
  details: string;
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
    lane: 'LEFT' | 'CENTER' | 'RIGHT';
    accelerationMps2: number;
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
  roundaboutApproachSpeedKmh?: number;
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
