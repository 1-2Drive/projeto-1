import { TestBed } from '@angular/core/testing';
import { REALTIME_PATH, RealtimeStreamService, STALE_AFTER_MS } from './realtime-stream.service';

class WebSocketMock {
  static instances: WebSocketMock[] = [];

  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  closedWith?: number;

  constructor(public readonly url: string) {
    WebSocketMock.instances.push(this);
  }

  receive(data: object | string): void {
    this.onmessage?.({ data: typeof data === 'string' ? data : JSON.stringify(data) } as MessageEvent<string>);
  }

  /** The connection ends without the client asking: refused, dropped, or the server going away. */
  drop(code = 1006): void {
    this.onclose?.({ code } as CloseEvent);
  }

  close(code?: number): void {
    this.closedWith = code;
  }
}

const SNAPSHOT_MESSAGE = {
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

describe('RealtimeStreamService', () => {
  const realWebSocket = window.WebSocket;
  let service: RealtimeStreamService;

  beforeEach(() => {
    (window as unknown as { WebSocket: unknown }).WebSocket = WebSocketMock;
    WebSocketMock.instances = [];
    TestBed.configureTestingModule({});
    service = TestBed.inject(RealtimeStreamService);
  });

  afterEach(() => {
    (window as unknown as { WebSocket: unknown }).WebSocket = realWebSocket;
  });

  it('connects to the backend WebSocket and emits snapshot messages', () => {
    const received: string[] = [];
    service.connect().subscribe((message) => received.push(`${message.event}:${message.payload.status}`));

    const socket = WebSocketMock.instances[0];
    expect(socket.url).toBe(`ws://${window.location.host}${REALTIME_PATH}`);
    socket.receive(SNAPSHOT_MESSAGE);
    socket.receive({ ...SNAPSHOT_MESSAGE, payload: { ...SNAPSHOT_MESSAGE.payload, status: 'PAUSED' } });

    expect(received).toEqual(['snapshot:RUNNING', 'snapshot:PAUSED']);
  });

  it('errors when the connection closes, so the caller can reconnect', () => {
    let error: unknown;
    service.connect().subscribe({ error: (err) => (error = err) });

    WebSocketMock.instances[0].drop(1001);

    expect(String(error)).toContain('code 1001');
  });

  it('errors on a frame that is not JSON and closes the socket', () => {
    let error: unknown;
    service.connect().subscribe({ error: (err) => (error = err) });

    const socket = WebSocketMock.instances[0];
    socket.receive('not json');

    expect(error).toBeInstanceOf(SyntaxError);
    expect(socket.closedWith).toBe(1000);
  });

  it('closes the socket when unsubscribed, without reporting an error', () => {
    let error: unknown;
    const subscription = service.connect().subscribe({ error: (err) => (error = err) });
    const socket = WebSocketMock.instances[0];

    subscription.unsubscribe();
    socket.drop(1000);

    expect(socket.closedWith).toBe(1000);
    expect(socket.onclose).toBeNull();
    expect(error).toBeUndefined();
  });

  describe('watchdog', () => {
    beforeEach(() => {
      jasmine.clock().install();
      jasmine.clock().mockDate(new Date(0));
    });

    afterEach(() => jasmine.clock().uninstall());

    it('keeps a socket that receives snapshots and gives up one that has gone silent', () => {
      let error: unknown;
      service.connect().subscribe({ error: (err) => (error = err) });
      const socket = WebSocketMock.instances[0];

      jasmine.clock().tick(STALE_AFTER_MS - 1000);
      socket.receive(SNAPSHOT_MESSAGE);
      jasmine.clock().tick(STALE_AFTER_MS);
      expect(error).toBeUndefined();

      jasmine.clock().tick(2000);
      expect(String(error)).toContain('silent');
      expect(socket.closedWith).toBe(1000);
    });
  });
});
