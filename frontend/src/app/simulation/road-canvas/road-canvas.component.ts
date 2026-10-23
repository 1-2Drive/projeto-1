import { NgTemplateOutlet } from '@angular/common';
import {
  AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, ElementRef, NgZone, computed,
  effect, inject, input, signal, viewChild
} from '@angular/core';
import { ApiSimulationSnapshot } from '../../core/models/api.models';
import {
  blendKinematics, EMPTY_KINEMATICS, isContinuation, Kinematics, kinematicsOf
} from './road-kinematics';
import { createCamera, Viewport } from './road-projection';
import { RainFrame, RainOccluder, rainIntensity, RainSimulation } from './road-rain';
import {
  buildEgo, buildItems, buildMarkings, buildRoadSurface, buildSensors, RoadItemView, RoadObjectKind
} from './road-scene';

export type { RoadObjectKind, RoadItemView, SensorView } from './road-scene';

const EMPTY_RANGES = {
  cameraRangeMeters: 0,
  radarRangeMeters: 0,
  blindSpotRangeMeters: 0,
  cameraRateMillis: 0,
  radarRateMillis: 0
};

/** Used until the first measurement, and whenever the canvas has no usable size (tests, hidden tabs). */
const DEFAULT_VIEWPORT: Viewport = { width: 960, height: 420 };
const MIN_USABLE_SIZE_PX = 80;
/** Undetected objects get a distance plate only when this close, so a busy road stays readable. */
const TAG_RANGE_METRES = 70;

/**
 * Which rain the view draws: true, the new one (drops in perspective, splashes, spray, wet-road
 * sheen and mist, road-rain.ts); false, the classic CSS overlay.
 */
export const USE_NEW_RAIN = true;

/** Size of what stands on the road, for hiding the splashes behind it (halves, metres). */
const OCCLUDER_SIZE: Partial<Record<RoadItemView['shape'], Omit<RainOccluder, 'lateral' | 'ahead'>>> = {
  ego: { halfWidth: 0.95, halfLength: 2.4, height: 1.45 },
  vehicle: { halfWidth: 0.9, halfLength: 2.2, height: 1.5 },
  pedestrian: { halfWidth: 0.3, halfLength: 0.2, height: 1.8 },
  barrier: { halfWidth: 1.6, halfLength: 0.2, height: 1.2 },
  obstacle: { halfWidth: 0.8, halfLength: 0.6, height: 0.9 }
};

/** A snapshot arrives about every tick; the drawn state slides to it over the observed gap. */
const MIN_BLEND_MILLIS = 60;
const MAX_BLEND_MILLIS = 250;
const DEFAULT_BLEND_MILLIS = 100;

interface Blend {
  from: Kinematics;
  start: number;
  duration: number;
}

/**
 * The driving view: a chase-camera, perspective rendering of the same world state the road-map
 * matrix shows. Geometry comes from road-projection.ts / road-scene.ts; this component only
 * feeds them the snapshot and draws the result. It never writes back into the simulation.
 */
@Component({
  selector: 'app-road-canvas',
  standalone: true,
  imports: [NgTemplateOutlet],
  templateUrl: './road-canvas.component.html',
  styleUrl: './road-canvas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RoadCanvasComponent implements AfterViewInit {
  readonly snapshot = input<ApiSimulationSnapshot | null>(null);
  /** Slide between consecutive snapshots at display rate; off, every snapshot is drawn as received. */
  readonly animate = input(true);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  private readonly viewport = signal<Viewport>(DEFAULT_VIEWPORT);
  private readonly target = computed(() => kinematicsOf(this.snapshot()));
  /** Bumped on every animation frame, so the drawn numbers are recomputed while a blend runs. */
  private readonly frameTick = signal(0);

  private blend: Blend | null = null;
  private lastTarget: Kinematics | null = null;
  private lastShown: Kinematics = EMPTY_KINEMATICS;
  private lastArrival = 0;
  private frameHandle = 0;
  private destroyed = false;

  /**
   * Numbers currently drawn: the newest snapshot, or a point on the way to it. Without a blend
   * (not running, first snapshot, a reset, `animate` off) this is simply the snapshot.
   */
  protected readonly kin = computed<Kinematics>(() => {
    const target = this.target();
    this.frameTick();
    return this.advance(target);
  });

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      if (this.frameHandle) {
        cancelAnimationFrame(this.frameHandle);
      }
      this.stopRain();
    });
    // The rain canvas exists only while the new rain is shown: start its loop with it.
    effect(() => {
      const canvas = this.rainCanvas()?.nativeElement;
      if (canvas) {
        this.startRain(canvas);
      } else {
        this.stopRain();
      }
    });
  }

  ngAfterViewInit(): void {
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver((entries) => {
      const rect = entries[entries.length - 1].contentRect;
      if (rect.width < MIN_USABLE_SIZE_PX || rect.height < MIN_USABLE_SIZE_PX) {
        return;
      }
      const current = this.viewport();
      if (Math.abs(current.width - rect.width) < 0.5 && Math.abs(current.height - rect.height) < 0.5) {
        return;
      }
      this.zone.run(() => this.viewport.set({ width: rect.width, height: rect.height }));
    });
    observer.observe(this.host.nativeElement);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  // ----- smoothing --------------------------------------------------------------------------

  /**
   * Position of the blend at this instant. It only touches component fields (never signals) and
   * is deterministic for a given target and clock, so it is safe inside a computed.
   */
  private advance(target: Kinematics): Kinematics {
    const now = performance.now();
    if (target !== this.lastTarget) {
      const from = this.lastShown;
      const gap = this.lastArrival ? now - this.lastArrival : DEFAULT_BLEND_MILLIS;
      this.lastArrival = now;
      this.lastTarget = target;
      const running = this.snapshot()?.status === 'RUNNING';
      this.blend = this.animate() && running && from !== EMPTY_KINEMATICS && isContinuation(from, target)
        ? { from, start: now, duration: Math.min(MAX_BLEND_MILLIS, Math.max(MIN_BLEND_MILLIS, gap)) }
        : null;
    }
    let shown = target;
    const blend = this.blend;
    if (blend) {
      const k = (now - blend.start) / blend.duration;
      if (k >= 1) {
        this.blend = null;
      } else {
        shown = blendKinematics(blend.from, target, Math.max(0, k));
        this.scheduleFrame();
      }
    }
    this.lastShown = shown;
    return shown;
  }

  private scheduleFrame(): void {
    if (this.frameHandle || this.destroyed) {
      return;
    }
    this.zone.runOutsideAngular(() => (this.frameHandle = requestAnimationFrame(this.onFrame)));
  }

  private readonly onFrame = (): void => {
    this.frameHandle = 0;
    if (this.destroyed) {
      return;
    }
    this.frameTick.update((n) => n + 1);
    // Only this view needs a refresh: skip a whole-application pass at display rate.
    this.cdr.detectChanges();
  };

  // ----- telemetry the HUD and the ego lamps read straight from the snapshot -----------------

  protected readonly ranges = computed(() => ({ ...EMPTY_RANGES, ...(this.snapshot()?.sensorRanges ?? {}) }));
  protected readonly egoPosition = computed(() => this.snapshot()?.egoVehicle?.positionMeters ?? 0);
  protected readonly egoSpeed = computed(() => this.snapshot()?.egoVehicle?.speedKmh ?? 0);
  protected readonly egoAcceleration = computed(() => this.snapshot()?.egoVehicle?.accelerationMps2 ?? 0);
  protected readonly currentLane = computed(() => this.snapshot()?.egoVehicle?.lane ?? 'RIGHT');
  protected readonly drivingState = computed(() => this.snapshot()?.egoVehicle?.drivingState ?? (this.egoSpeed() > 0.5 ? 'CRUISING' : 'STOPPED'));
  protected readonly indicatorName = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState && ego.indicatorState !== 'OFF') {
      return ego.indicatorState;
    }
    const m = this.snapshot()?.maneuver;
    if (m?.indicatorsLeft) return 'LEFT';
    if (m?.indicatorsRight) return 'RIGHT';
    return 'OFF';
  });
  protected readonly steeringName = computed(() => {
    return this.snapshot()?.egoVehicle?.steeringState ?? this.snapshot()?.maneuver?.steering ?? 'STRAIGHT';
  });
  protected readonly leftMarking = computed(() => this.snapshot()?.roadMarks?.leftMarking ?? 'DASHED');
  protected readonly rightMarking = computed(() => this.snapshot()?.roadMarks?.rightMarking ?? 'DASHED');
  protected readonly indicatorHazard = computed(() => this.indicatorName() === 'HAZARD');
  protected readonly maneuverState = computed(() => this.snapshot()?.maneuver?.state ?? 'IDLE');
  protected readonly indicatorLeft = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState === 'LEFT') return true;
    return this.snapshot()?.maneuver?.indicatorsLeft ?? false;
  });
  protected readonly indicatorRight = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.indicatorState === 'RIGHT') return true;
    return this.snapshot()?.maneuver?.indicatorsRight ?? false;
  });
  protected readonly braking = computed(() => {
    const ego = this.snapshot()?.egoVehicle;
    if (ego?.drivingState === 'BRAKING' || (ego?.accelerationMps2 ?? 0) < -0.8) return true;
    return this.snapshot()?.maneuver?.braking ?? false;
  });

  /**
   * Range the camera can actually see. The backend publishes it as
   * sensors.environment.effectiveRangeMeters (camera range x visibility factor);
   * without telemetry the nominal range is assumed.
   */
  protected readonly effectiveCameraRange = computed(() => {
    const nominal = this.ranges().cameraRangeMeters;
    const effective = this.snapshot()?.sensors?.environment?.effectiveRangeMeters;
    if (effective != null && Number.isFinite(effective) && effective >= 0 && effective < nominal) {
      return effective;
    }
    return nominal;
  });

  protected readonly cameraDegraded = computed(() => this.effectiveCameraRange() < this.ranges().cameraRangeMeters - 0.5);

  /** "CAMERA FOV (120 m)" in clear weather; "CAMERA 84 m (120 x 70%)" when visibility shortens it. */
  protected readonly cameraLabel = computed(() => {
    const nominal = Math.round(this.ranges().cameraRangeMeters);
    if (!this.cameraDegraded()) {
      return `CAMERA FOV (${nominal} m)`;
    }
    const effective = Math.round(this.effectiveCameraRange());
    const percent = Math.round((this.effectiveCameraRange() / this.ranges().cameraRangeMeters) * 100);
    return `CAMERA ${effective} m (${nominal} \u00d7 ${percent}%)`;
  });

  /** True before the first tick so the UI can invite the user to press START. */
  protected readonly awaitingStart = computed(() => {
    const snapshot = this.snapshot();
    return !!snapshot && snapshot.simulationTimeMillis === 0 && snapshot.status !== 'RUNNING';
  });

  protected readonly running = computed(() => this.snapshot()?.status === 'RUNNING');

  protected readonly raining = computed(() => {
    const snapshot = this.snapshot();
    return snapshot?.environment?.weatherCondition === 'RAIN'
      || snapshot?.sensors?.environment?.rain === true;
  });

  /** The new rain is drawn while it rains (USE_NEW_RAIN); otherwise the classic overlay. */
  readonly useNewRain = input(USE_NEW_RAIN);
  protected readonly newRain = computed(() => this.raining() && this.useNewRain());

  /** How hard it rains, from the visibility the backend reports. Drawing only. */
  protected readonly rainIntensity = computed(() => rainIntensity(this.snapshot()?.environment?.visibilityFactor));

  /**
   * Distance haze of the new rain: thick at the horizon and gone well before the ego, reaching
   * about half its strength where the camera stops seeing (the effective range).
   */
  protected readonly rainMist = computed(() => {
    const camera = this.camera();
    const yAt = (ahead: number) => camera.horizonY + (camera.eye * camera.focal) / (Math.max(1, ahead) + camera.back);
    const range = Math.max(20, this.effectiveCameraRange());
    const top = Math.max(0, camera.horizonY - camera.height * 0.14);
    const bottom = Math.min(camera.egoBaseY - 4, yAt(range * 0.3));
    const span = Math.max(1, bottom - top);
    const intensity = this.rainIntensity();
    return {
      top,
      bottom,
      height: span,
      horizonOffset: ((camera.horizonY - top) / span).toFixed(3),
      rangeOffset: Math.min(0.98, Math.max(0.05, (yAt(range) - top) / span)).toFixed(3),
      peak: (0.42 + 0.4 * intensity).toFixed(2),
      half: (0.2 + 0.25 * intensity).toFixed(2)
    };
  });

  /** "RAIN · VIS 70% · GRIP 65%": the factors the knowledge base is reasoning with. */
  protected readonly rainChipText = computed(() => {
    const env = this.snapshot()?.environment;
    const visibility = Math.round((env?.visibilityFactor ?? 1) * 100);
    const grip = Math.round((env?.gripFactor ?? 1) * 100);
    return `RAIN \u00b7 VIS ${visibility}% \u00b7 GRIP ${grip}%`;
  });

  // ----- the perspective scene --------------------------------------------------------------

  protected readonly camera = computed(() => createCamera(this.viewport(), this.kin().egoLateral));
  protected readonly viewBox = computed(() => `0 0 ${this.camera().width.toFixed(1)} ${this.camera().height.toFixed(1)}`);
  protected readonly surface = computed(() => buildRoadSurface(this.camera()));
  protected readonly markings = computed(() =>
    buildMarkings(this.camera(), this.kin().egoPos, this.leftMarking(), this.rightMarking()));

  protected readonly sensors = computed(() => buildSensors({
    camera: this.camera(),
    egoLateral: this.kin().egoLateral,
    egoPos: this.kin().egoPos,
    lane: this.currentLane(),
    ranges: this.ranges(),
    effectiveCameraRange: this.effectiveCameraRange(),
    cameraLabel: this.cameraLabel(),
    snapshot: this.snapshot()
  }));

  private readonly items = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot) {
      return { flat: [] as RoadItemView[], standing: [] as RoadItemView[] };
    }
    const ranges = this.ranges();
    return buildItems({
      camera: this.camera(),
      kin: this.kin(),
      snapshot,
      egoLane: this.currentLane(),
      tagRangeMeters: Math.min(TAG_RANGE_METRES, Math.max(ranges.cameraRangeMeters, ranges.radarRangeMeters))
    });
  });

  /** Painted on the road surface (crosswalks, potholes, stop lines, speed-limit paint). */
  protected readonly flatItems = computed(() => this.items().flat.slice().sort((a, b) => b.depth - a.depth));

  /** Everything that stands on the road, far to near, the ego among them. */
  protected readonly standingItems = computed<RoadItemView[]>(() => {
    const ego = buildEgo({
      camera: this.camera(),
      lateral: this.kin().egoLateral,
      headingDegrees: this.kin().heading,
      steering: this.steeringName()
    });
    return [...this.items().standing, ego].sort((a, b) => b.depth - a.depth);
  });

  // ----- the new rain: a canvas animated on its own, outside Angular --------------------------

  private readonly rainCanvas = viewChild<ElementRef<HTMLCanvasElement>>('rainCanvas');
  private rainHandle = 0;
  private rainCanvasEl: HTMLCanvasElement | null = null;
  private rain: RainSimulation | null = null;
  private rainLast = 0;

  /** What the rain needs this instant: the camera, the ego's motion and what hides the road. */
  private rainFrame(): RainFrame {
    const occluders: RainOccluder[] = [];
    for (const item of this.standingItems()) {
      const size = OCCLUDER_SIZE[item.shape];
      if (size && item.role !== 'copy') {
        occluders.push({ lateral: item.lateral, ahead: item.ahead, ...size });
      }
    }
    return {
      camera: this.camera(),
      egoPos: this.kin().egoPos,
      egoLateral: this.kin().egoLateral,
      speedMps: this.running() ? Math.max(0, this.egoSpeed()) / 3.6 : 0,
      intensity: this.rainIntensity(),
      occluders
    };
  }

  private startRain(canvas: HTMLCanvasElement): void {
    if (this.rainCanvasEl === canvas && this.rainHandle) {
      return;
    }
    this.stopRain();
    const ctx = canvas.getContext('2d');
    if (!ctx || typeof requestAnimationFrame === 'undefined') {
      return;
    }
    this.rainCanvasEl = canvas;
    this.rain ??= new RainSimulation();
    const reduced = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.rainLast = 0;
    const tick = (now: number) => {
      this.rainHandle = 0;
      if (this.destroyed || this.rainCanvasEl !== canvas || !this.rain) {
        return;
      }
      const frame = this.rainFrame();
      const ratio = Math.min(2, globalThis.devicePixelRatio || 1);
      const width = Math.round(frame.camera.width * ratio);
      const height = Math.round(frame.camera.height * ratio);
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      // Reduced motion: the shower is drawn, but stands still.
      const dt = reduced || !this.rainLast ? 0 : (now - this.rainLast) / 1000;
      this.rainLast = now;
      this.rain.step(frame, dt);
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      this.rain.draw(ctx, frame);
      if (!reduced) {
        this.rainHandle = requestAnimationFrame(tick);
      }
    };
    this.zone.runOutsideAngular(() => (this.rainHandle = requestAnimationFrame(tick)));
  }

  private stopRain(): void {
    if (this.rainHandle) {
      cancelAnimationFrame(this.rainHandle);
      this.rainHandle = 0;
    }
    this.rainCanvasEl = null;
  }

  protected testIdFor(item: RoadItemView): string | null {
    if (item.role === 'decor') {
      return null;
    }
    const base = this.baseTestId(item.kind);
    return item.role === 'copy' ? `${base}-mark` : base;
  }

  private baseTestId(kind: RoadObjectKind): string {
    switch (kind) {
      case 'obstacle': return 'road-obstacle';
      case 'pothole': return 'road-pothole';
      case 'barrier': return 'road-barrier';
      case 'stopSign': return 'road-stop-sign';
      case 'trafficLight': return 'road-traffic-light';
      case 'speedLimit': return 'road-speed-limit';
      case 'vehicle': return 'road-vehicle';
      case 'pedestrian': return 'road-pedestrian';
      case 'crosswalk': return 'crosswalk';
      case 'radar': return 'road-radar';
      default: return 'road-' + kind;
    }
  }

  protected trackById(_index: number, item: RoadItemView): string {
    return `${item.id}:${item.shape}:${item.role}:${item.lane}`;
  }
}
