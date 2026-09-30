import { TestBed } from '@angular/core/testing';
import { RealtimeStreamService } from './realtime-stream.service';

class EventSourceMock {
  static instances: EventSourceMock[] = [];

  onerror: ((err: unknown) => void) | null = null;
  private readonly listeners: Record<string, Array<(event: MessageEvent<string>) => void>> = {};

  constructor(public readonly url: string) {
    EventSourceMock.instances.push(this);
  }

  addEventListener(type: string, handler: (event: MessageEvent<string>) => void): void {
    this.listeners[type] = this.listeners[type] ?? [];
    this.listeners[type].push(handler);
  }

  emit(type: string, data: object): void {
    (this.listeners[type] ?? []).forEach((handler) => handler({ data: JSON.stringify(data) } as MessageEvent<string>));
  }

  close(): void {
    // noop
  }
}

describe('RealtimeStreamService', () => {
  beforeEach(() => {
    (window as unknown as { EventSource: typeof EventSource }).EventSource = EventSourceMock as unknown as typeof EventSource;
    EventSourceMock.instances = [];
    TestBed.configureTestingModule({});
  });

  it('connects to SSE stream and emits snapshot messages', (done) => {
    const service = TestBed.inject(RealtimeStreamService);

    service.connect().subscribe({
      next: (message) => {
        expect(message.event).toBe('snapshot');
        expect(message.payload.status).toBe('RUNNING');
        done();
      }
    });

    const source = EventSourceMock.instances[0];
    expect(source.url).toBe('/api/stream/snapshots');
    source.emit('snapshot', {
      event: 'snapshot',
      payload: {
        simulationTimeMillis: 100,
        status: 'RUNNING',
        egoVehicle: { speedKmh: 40, positionMeters: 10, lane: 'CENTER', accelerationMps2: 0 },
        environment: { weatherCondition: 'CLEAR', visibilityFactor: 1, gripFactor: 1 },
        speedAdvisor: { currentKmh: 40, legalLimitKmh: 120, recommendedKmh: 90, action: 'MAINTAIN_SPEED' },
        currentDecision: {
          action: 'MAINTAIN_SPEED',
          recommendedSpeedKmh: 90,
          riskLevel: 'LOW',
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
        activeStage: 'WORLD',
        tickMillis: 100,
        detectedObjectIds: [],
        cameraDetectedObjectIds: [],
        radarDetectedObjectIds: [],
        demoPhases: []
      }
    });
  });
});
