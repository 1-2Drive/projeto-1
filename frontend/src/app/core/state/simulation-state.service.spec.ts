import { TestBed } from '@angular/core/testing';
import { SimulationStateService } from './simulation-state.service';
import { BackendApiService } from '../api/backend-api.service';
import { RealtimeStreamService } from '../websocket/realtime-stream.service';
import { Observable, of } from 'rxjs';

class BackendApiServiceMock {
  getState() {
    return of({
      simulationTimeMillis: 0,
      status: 'STOPPED',
      egoVehicle: { speedKmh: 0, positionMeters: 0, lane: 'CENTER', accelerationMps2: 0 },
      environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
      speedAdvisor: { currentKmh: 0, legalLimitKmh: 120, recommendedKmh: 0, action: 'MAINTAIN_SPEED' },
      currentDecision: {
        action: 'MAINTAIN_SPEED',
        recommendedSpeedKmh: 0,
        riskLevel: 'LOW',
        reason: '',
        sourceRule: '',
        timestamp: 0,
        evidence: []
      },
      sensorReadings: {},
      worldObjects: [],
      workingMemory: [],
      recentTimeline: [],
      recentRules: [],
      recentFacts: [],
      activeStage: 'WORLD',
      tickMillis: 100,
      detectedObjectIds: [],
      cameraDetectedObjectIds: [],
      radarDetectedObjectIds: [],
      demoPhases: []
    });
  }

  getSystemStatus() {
    return of({ backend: 'CONNECTED', drools: 'ACTIVE', simulation: 'STOPPED' });
  }

  getScenarios() {
    return of([{ id: 'S1', name: 'Scenario', initialSpeedKmh: 60, initialWeather: 'CLEAR', roadElementCount: 0, actorCount: 0, timedActionCount: 0 }]);
  }

  startSimulation() {
    return of({ success: true, message: 'ok' });
  }

  pauseSimulation() {
    return of({ success: true, message: 'ok' });
  }

  resumeSimulation() {
    return of({ success: true, message: 'ok' });
  }

  stopSimulation() {
    return of({ success: true, message: 'ok' });
  }

  resetSimulation() {
    return of({ success: true, message: 'ok' });
  }

  loadScenario() {
    return of({ success: true, message: 'ok' });
  }

  addRoadElement() {
    return of({ success: true, objectId: 'x', message: 'ok' });
  }

  addActor() {
    return of({ success: true, objectId: 'x', message: 'ok' });
  }

  updateEnvironment() {
    return of({ success: true, message: 'ok' });
  }
}

class RealtimeStreamServiceMock {
  connect(): Observable<any> {
    return of({
      event: 'snapshot',
      payload: {
        simulationTimeMillis: 100,
        status: 'RUNNING',
        egoVehicle: { speedKmh: 40, positionMeters: 10, lane: 'CENTER', accelerationMps2: 0 },
        environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
        speedAdvisor: { currentKmh: 40, legalLimitKmh: 50, recommendedKmh: 50, action: 'REDUCE_SPEED' },
        currentDecision: {
          action: 'REDUCE_SPEED',
          recommendedSpeedKmh: 50,
          riskLevel: 'MODERATE',
          reason: '',
          sourceRule: '',
          timestamp: 100,
          evidence: []
        },
        sensorReadings: {},
        worldObjects: [],
        workingMemory: [],
        recentTimeline: [],
        recentRules: [],
        recentFacts: [],
        activeStage: 'DECISION',
        tickMillis: 100,
        detectedObjectIds: [],
        cameraDetectedObjectIds: [],
        radarDetectedObjectIds: [],
        demoPhases: []
      }
    });
  }
}

describe('SimulationStateService', () => {
  let service: SimulationStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SimulationStateService,
        { provide: BackendApiService, useClass: BackendApiServiceMock },
        { provide: RealtimeStreamService, useClass: RealtimeStreamServiceMock }
      ]
    });
    service = TestBed.inject(SimulationStateService);
  });

  it('should initialize and apply realtime snapshot', () => {
    service.initialize();

    expect(service.snapshot()?.status).toBe('RUNNING');
    expect(service.connection().backendConnected).toBeTrue();
    expect(service.connection().streamStatus).toBe('LIVE');
    expect(service.scenarios().length).toBe(1);
  });
});
