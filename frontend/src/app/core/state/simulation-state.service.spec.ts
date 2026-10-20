import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { SimulationStateService } from './simulation-state.service';
import { BackendApiService } from '../api/backend-api.service';
import { RealtimeStreamService } from '../websocket/realtime-stream.service';
import { Observable, of, Subject, throwError } from 'rxjs';

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
      sensorRanges: {
        cameraRangeMeters: 120,
        radarRangeMeters: 80,
        blindSpotRangeMeters: 25,
        cameraRateMillis: 200,
        radarRateMillis: 100
      },
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
    return of({ success: true, message: 'ok', sessionId: 'saved-session' as string | null });
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

  switchBackend(_backend: string) {
    return of({ success: true, message: 'ok' });
  }
}

const REALTIME_MESSAGE = {
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
    sensorRanges: {
      cameraRangeMeters: 120,
      radarRangeMeters: 80,
      blindSpotRangeMeters: 25,
      cameraRateMillis: 200,
      radarRateMillis: 100
    },
    demoPhases: []
  }
};

class RealtimeStreamServiceMock {
  connect(): Observable<any> {
    return of(REALTIME_MESSAGE);
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

  it('reports Drools as the rule engine when the snapshot does not name one', () => {
    service.initialize();

    expect(service.expertBackend()).toBe('DROOLS');
    expect(service.expertBackendError()).toBeNull();
  });

  it('asks the backend to switch rule engine, but not to the one already deciding', () => {
    const api = TestBed.inject(BackendApiService) as unknown as BackendApiServiceMock;
    const switchSpy = spyOn(api, 'switchBackend').and.callThrough();
    service.initialize();

    service.switchBackend('DROOLS');
    expect(switchSpy).not.toHaveBeenCalled();

    service.switchBackend('PROLOG');
    expect(switchSpy).toHaveBeenCalledOnceWith('PROLOG');
    expect(service.connection().simulationStatus).toBe('STOPPED');
    expect(service.loading()).toBeFalse();
  });

  it('reconnects a dropped realtime channel, waiting longer after each failed attempt', fakeAsync(() => {
    const stream = TestBed.inject(RealtimeStreamService);
    const connections: Subject<any>[] = [];
    spyOn(stream, 'connect').and.callFake(() => {
      const connection = new Subject<any>();
      connections.push(connection);
      return connection;
    });
    service.initialize();
    connections[0].next(REALTIME_MESSAGE);
    expect(service.connection().streamStatus).toBe('LIVE');

    connections[0].error(new Error('closed'));
    expect(service.connection().streamStatus).toBe('RECONNECTING');
    expect(service.connection().backendConnected).toBeFalse();
    expect(service.error()).toContain('reconnect');
    tick(999);
    expect(connections.length).toBe(1);
    tick(1);
    expect(connections.length).toBe(2);

    connections[1].error(new Error('refused'));
    tick(1999);
    expect(connections.length).toBe(2);
    tick(1);
    expect(connections.length).toBe(3);

    // A snapshot means the channel is back: live again, and the next drop waits the least.
    connections[2].next(REALTIME_MESSAGE);
    expect(service.connection().streamStatus).toBe('LIVE');
    expect(service.connection().backendConnected).toBeTrue();
    expect(service.error()).toBeNull();
    connections[2].error(new Error('closed'));
    tick(1000);
    expect(connections.length).toBe(4);

    service.destroy();
    expect(service.connection().streamStatus).toBe('DISCONNECTED');
    expect(connections[3].observed).toBeFalse();
  }));

  it('shows why a switch failed', () => {
    const api = TestBed.inject(BackendApiService) as unknown as BackendApiServiceMock;
    spyOn(api, 'switchBackend').and.returnValue(of({ success: false, message: 'swipl not found' }));
    service.initialize();

    service.switchBackend('PROLOG');
    expect(service.error()).toBe('swipl not found');
  });

  it('opens the just-stopped persisted session after STOP succeeds', () => {
    service.initialize();
    service.stop();

    expect(service.stoppedSessionId()).toBe('saved-session');
    expect(service.connection().simulationStatus).toBe('STOPPED');
    expect(service.stopPersistenceError()).toBeNull();
  });

  it('does not open a query popup when session persistence fails', () => {
    const api = TestBed.inject(BackendApiService) as unknown as BackendApiServiceMock;
    spyOn(api, 'stopSimulation').and.returnValue(of({
      success: false,
      message: 'Simulation stopped, but the session could not be saved. Historical WHY/WHY-NOT is unavailable for this trip.',
      sessionId: null
    }));
    service.initialize();
    service.stop();

    expect(service.stoppedSessionId()).toBeNull();
    expect(service.stopPersistenceError()).toContain('could not be saved');
  });

  it('does not delete completed session history when RESET is requested', () => {
    service.initialize();
    service.stop();
    service.reset();

    expect(service.stoppedSessionId()).toBe('saved-session');
  });

  it('reports persistence failure returned as an HTTP error while leaving simulation stopped', () => {
    const api = TestBed.inject(BackendApiService) as unknown as BackendApiServiceMock;
    spyOn(api, 'stopSimulation').and.returnValue(throwError(() => ({
      error: { message: 'Simulation stopped, but the session could not be saved.' }
    })));
    service.initialize();
    service.stop();

    expect(service.stoppedSessionId()).toBeNull();
    expect(service.connection().simulationStatus).toBe('STOPPED');
    expect(service.stopPersistenceError()).toContain('could not be saved');
  });
});
