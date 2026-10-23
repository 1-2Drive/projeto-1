/**
 * The rain of the driving view: falling drops, splashes on the road and the spray behind the
 * ego, simulated in the chase camera's world and drawn on a canvas over the scene.
 *
 * Presentation only. It reads the camera, the ego's motion and the visibility the backend
 * reports; nothing here is fed back to the simulation. Drops live in camera space (lateral
 * metres from the camera, height above the road, depth ahead of the camera), so they stream
 * towards the camera at the ego's speed. Splashes and spray are pinned to road positions
 * (metres along the road), so they stay on the asphalt and slide past as the ego drives on.
 */
import { ASPHALT_HALF_METRES, Camera, ground, project, ScreenPoint } from './road-projection';

/** Fall speed of a raindrop, m/s (terminal velocity of a few-millimetre drop). */
const FALL_MPS = 8.5;
/** Steady sideways drift, m/s; tilts the streaks slightly. */
const WIND_MPS = 1.1;
/** How long a streak stays on the eye, seconds: its length is the distance moved in that time. */
const EXPOSURE_S = 0.03;
/** Longest streak drawn, CSS pixels: a drop right in front of the lens is not a bar across the view. */
const MAX_STREAK_PX = 34;
/** Depths between which drops exist, metres from the camera. */
const NEAR_Z = 6;
const FAR_Z = 95;
/** Splashes only where they are big enough to see. */
const SPLASH_MAX_Z = 70;
const SPLASH_LIFE_S = 0.42;
const SPLASH_MAX_RADIUS_M = 0.45;
/** Drops per million screen pixels at full intensity. */
const DROPS_PER_MEGAPIXEL = 1700;
const MAX_DROPS = 2200;
const SPRAY_LIFE_S = 0.9;
/** Below this ego speed the wheels throw no spray, m/s. */
const SPRAY_MIN_MPS = 4;
/** Ego footprint (EGO_CAR, road-ev-model.ts): rear wheels about this far behind its centre. */
const EGO_REAR_AHEAD_M = -1.7;
const EGO_TRACK_HALF_M = 0.8;
/** Far and faint, middle, near and bright. */
const DROP_STYLES = [
  { colour: 'rgba(196, 222, 240, 0.15)', width: 0.7 },
  { colour: 'rgba(206, 230, 246, 0.24)', width: 0.9 },
  { colour: 'rgba(222, 240, 252, 0.34)', width: 1.15 }
];

/** What occludes the road behind it: a car, a pedestrian, a barrier, as a box on the road. */
export interface RainOccluder {
  lateral: number;
  /** Metres ahead of the ego of the box centre. */
  ahead: number;
  halfWidth: number;
  halfLength: number;
  height: number;
}

export interface RainFrame {
  camera: Camera;
  /** Ego position along the road, metres (what splashes and spray are pinned to). */
  egoPos: number;
  egoLateral: number;
  /** Ego speed, m/s. */
  speedMps: number;
  /** 0 (drizzle) to 1 (downpour); see rainIntensity. */
  intensity: number;
  occluders: RainOccluder[];
}

interface Drop {
  /** Metres from the camera's lateral position. */
  x: number;
  /** Metres above the road. */
  h: number;
  /** Camera depth, metres. */
  z: number;
  /** Fall speed of this drop, m/s. */
  vy: number;
  /** Brightness of this drop, 0-1. */
  tone: number;
}

interface Splash {
  lateral: number;
  /** Metres along the road. */
  road: number;
  age: number;
}

interface Puff {
  lateral: number;
  road: number;
  h: number;
  vh: number;
  vLat: number;
  age: number;
}

/**
 * How hard it rains for a visibility factor (1 is clear, rain in the simulation gives 0.7).
 * Never quite nothing, so rain with good visibility is still drawn as a light shower.
 */
export function rainIntensity(visibilityFactor: number | null | undefined): number {
  const visibility = visibilityFactor != null && Number.isFinite(visibilityFactor) ? visibilityFactor : 0.7;
  return Math.max(0.25, Math.min(1, (1 - visibility) / 0.5));
}

/** Number of drops for a canvas size and intensity. */
export function dropCount(width: number, height: number, intensity: number): number {
  return Math.min(MAX_DROPS, Math.round((width * height / 1e6) * DROPS_PER_MEGAPIXEL * intensity));
}

export class RainSimulation {
  private drops: Drop[] = [];
  private splashes: Splash[] = [];
  private puffs: Puff[] = [];
  private sprayDebt = 0;

  constructor(private readonly random: () => number = Math.random) {
  }

  get dropTotal(): number {
    return this.drops.length;
  }

  get splashTotal(): number {
    return this.splashes.length;
  }

  get puffTotal(): number {
    return this.puffs.length;
  }

  /** Advances the shower by dt seconds (clamped, so a background tab does not jump). */
  step(frame: RainFrame, dtSeconds: number): void {
    const dt = Math.max(0, Math.min(0.05, dtSeconds));
    this.resize(frame);
    const camera = frame.camera;
    for (const drop of this.drops) {
      drop.h -= drop.vy * dt;
      drop.x += WIND_MPS * dt;
      drop.z -= frame.speedMps * dt;
      if (drop.h <= 0) {
        this.land(frame, drop);
        this.respawn(drop, camera, false);
      } else if (drop.z < NEAR_Z || !this.onScreen(drop, camera)) {
        this.respawn(drop, camera, drop.z < NEAR_Z);
      }
    }
    for (const splash of this.splashes) {
      splash.age += dt;
    }
    this.splashes = this.splashes.filter((s) => s.age < SPLASH_LIFE_S);
    this.stepSpray(frame, dt);
  }

  /** Draws the shower; the canvas is in CSS pixels (the caller scales for the device ratio). */
  draw(ctx: CanvasRenderingContext2D, frame: RainFrame): void {
    const camera = frame.camera;
    ctx.clearRect(0, 0, camera.width, camera.height);
    this.drawSplashes(ctx, frame);
    this.drawSpray(ctx, frame);
    this.drawDrops(ctx, frame);
  }

  // ----- drops --------------------------------------------------------------------------

  private resize(frame: RainFrame): void {
    const wanted = dropCount(frame.camera.width, frame.camera.height, frame.intensity);
    while (this.drops.length < wanted) {
      const drop: Drop = { x: 0, h: 0, z: 0, vy: FALL_MPS, tone: 1 };
      this.respawn(drop, frame.camera, false, true);
      this.drops.push(drop);
    }
    if (this.drops.length > wanted) {
      this.drops.length = wanted;
    }
  }

  /** Puts a drop back: anywhere in view at start, else at the top (or far away when passed). */
  private respawn(drop: Drop, camera: Camera, far: boolean, anywhere = false): void {
    // Depth uniform in 1/z spreads drops evenly over the screen instead of crowding the horizon.
    const inv = 1 / FAR_Z + this.random() * (1 / NEAR_Z - 1 / FAR_Z);
    drop.z = far ? FAR_Z * (0.75 + 0.25 * this.random()) : 1 / inv;
    const halfSpan = (camera.width / 2) / (camera.focal / drop.z) * 1.15;
    drop.x = (this.random() * 2 - 1) * halfSpan;
    const top = camera.eye + (camera.horizonY * drop.z) / camera.focal;
    drop.h = anywhere || far ? this.random() * top : top * (0.92 + 0.08 * this.random());
    drop.vy = FALL_MPS * (0.85 + 0.3 * this.random());
    drop.tone = 0.45 + 0.55 * this.random();
  }

  private onScreen(drop: Drop, camera: Camera): boolean {
    const scale = camera.focal / drop.z;
    const x = camera.cx + drop.x * scale;
    return x > -40 && x < camera.width + 40;
  }

  private drawDrops(ctx: CanvasRenderingContext2D, frame: RainFrame): void {
    const camera = frame.camera;
    ctx.lineCap = 'round';
    // Drops are stroked in three brightness bands (far and faint to near and bright), one path
    // each, so a frame changes the stroke style three times instead of once per drop.
    const bands: Drop[][] = [[], [], []];
    for (const drop of this.drops) {
      const nearness = 1 - (drop.z - NEAR_Z) / (FAR_Z - NEAR_Z);
      const brightness = drop.tone * (0.35 + 0.65 * nearness);
      bands[brightness < 0.4 ? 0 : brightness < 0.65 ? 1 : 2].push(drop);
    }
    bands.forEach((drops, band) => {
      ctx.strokeStyle = DROP_STYLES[band].colour;
      ctx.lineWidth = DROP_STYLES[band].width;
      ctx.beginPath();
      for (const drop of drops) {
        const head = this.screen(camera, drop.x, drop.h, drop.z);
        const tail = this.screen(camera, drop.x - WIND_MPS * EXPOSURE_S, drop.h + drop.vy * EXPOSURE_S,
          drop.z + frame.speedMps * EXPOSURE_S);
        const dx = tail.x - head.x;
        const dy = tail.y - head.y;
        const length = Math.hypot(dx, dy);
        const k = length > MAX_STREAK_PX ? MAX_STREAK_PX / length : 1;
        ctx.moveTo(head.x + dx * k, head.y + dy * k);
        ctx.lineTo(head.x, head.y);
      }
      ctx.stroke();
    });
  }

  private screen(camera: Camera, x: number, h: number, z: number): { x: number; y: number } {
    const scale = camera.focal / Math.max(0.5, z);
    return { x: camera.cx + x * scale, y: camera.horizonY + (camera.eye - h) * scale };
  }

  // ----- splashes -----------------------------------------------------------------------

  /** A drop reached the road: leave a splash where it hit, if that is on the asphalt and close. */
  private land(frame: RainFrame, drop: Drop): void {
    const lateral = frame.camera.x + drop.x;
    if (drop.z > SPLASH_MAX_Z || Math.abs(lateral) > ASPHALT_HALF_METRES || this.random() > 0.55) {
      return;
    }
    const ahead = drop.z - frame.camera.back;
    this.splashes.push({ lateral, road: frame.egoPos + ahead, age: 0 });
  }

  private drawSplashes(ctx: CanvasRenderingContext2D, frame: RainFrame): void {
    const camera = frame.camera;
    ctx.lineWidth = 1.2;
    for (const splash of this.splashes) {
      const ahead = splash.road - frame.egoPos;
      const point = ground(camera, splash.lateral, ahead);
      if (!point || this.hidden(frame, splash.lateral, ahead, point)) {
        continue;
      }
      const k = splash.age / SPLASH_LIFE_S;
      const scale = camera.focal / point.z;
      const rx = Math.max(0.6, SPLASH_MAX_RADIUS_M * (0.25 + 0.75 * k) * scale);
      // Seen from the camera, a ring on the road is flattened by eye / depth.
      // Never thinner than a pixel and a bit, or a far splash vanishes into a line.
      const ry = Math.max(1.3, rx * Math.min(1, camera.eye / point.z));
      const alpha = 0.62 * (1 - k) * (1 - point.z / (SPLASH_MAX_Z * 1.15));
      ctx.strokeStyle = `rgba(214, 236, 250, ${alpha.toFixed(3)})`;
      ctx.beginPath();
      ctx.ellipse(point.x, point.y, rx, ry, 0, 0, Math.PI * 2);
      ctx.stroke();
      if (k < 0.35) {
        // The crown of the splash: two droplets thrown up for the first instant.
        ctx.fillStyle = `rgba(226, 242, 252, ${(alpha * 1.2).toFixed(3)})`;
        const up = (0.35 - k) * 0.25 * scale;
        ctx.fillRect(point.x - rx * 0.6, point.y - up - ry, 1.2, 1.2);
        ctx.fillRect(point.x + rx * 0.5, point.y - up * 0.8 - ry, 1.2, 1.2);
      }
    }
  }

  /**
   * True when a point on the road at (lateral, ahead) is behind something standing on the road
   * (as seen from the camera), so its splash would be drawn over that object.
   */
  private hidden(frame: RainFrame, lateral: number, ahead: number, point: ScreenPoint): boolean {
    const camera = frame.camera;
    for (const o of frame.occluders) {
      if (ahead <= o.ahead - o.halfLength) {
        continue;
      }
      const nearLeft = project(camera, o.lateral - o.halfWidth, 0, o.ahead - o.halfLength);
      const nearRight = project(camera, o.lateral + o.halfWidth, 0, o.ahead - o.halfLength);
      const farRoof = project(camera, o.lateral, o.height, o.ahead + o.halfLength);
      if (!nearLeft || !nearRight || !farRoof) {
        continue;
      }
      if (point.x >= nearLeft.x && point.x <= nearRight.x && point.y >= farRoof.y && point.y <= nearLeft.y) {
        return true;
      }
    }
    return false;
  }

  // ----- spray behind the ego -------------------------------------------------------------

  private stepSpray(frame: RainFrame, dt: number): void {
    for (const puff of this.puffs) {
      puff.age += dt;
      puff.h = Math.max(0, puff.h + puff.vh * dt);
      puff.vh *= 0.9;
      puff.lateral += puff.vLat * dt;
    }
    this.puffs = this.puffs.filter((p) => p.age < SPRAY_LIFE_S);
    if (frame.speedMps < SPRAY_MIN_MPS) {
      this.sprayDebt = 0;
      return;
    }
    // More spray the faster the ego goes and the harder it rains.
    this.sprayDebt += dt * Math.min(60, frame.speedMps * 2.2) * (0.5 + frame.intensity);
    while (this.sprayDebt >= 1) {
      this.sprayDebt -= 1;
      const side = this.random() < 0.5 ? -1 : 1;
      this.puffs.push({
        lateral: frame.egoLateral + side * EGO_TRACK_HALF_M + (this.random() - 0.5) * 0.3,
        road: frame.egoPos + EGO_REAR_AHEAD_M - this.random() * 0.6,
        h: 0.1,
        vh: 0.8 + this.random() * 0.9,
        vLat: side * (0.2 + this.random() * 0.5),
        age: 0
      });
    }
  }

  private drawSpray(ctx: CanvasRenderingContext2D, frame: RainFrame): void {
    const camera = frame.camera;
    for (const puff of this.puffs) {
      const ahead = puff.road - frame.egoPos;
      const point = project(camera, puff.lateral, puff.h, ahead);
      if (!point) {
        continue;
      }
      const k = puff.age / SPRAY_LIFE_S;
      const radius = (0.25 + 0.9 * k) * (camera.focal / point.z);
      const alpha = 0.22 * (1 - k) * Math.min(1, frame.speedMps / 15);
      const gradient = ctx.createRadialGradient(point.x, point.y, 0, point.x, point.y, radius);
      gradient.addColorStop(0, `rgba(205, 222, 236, ${alpha.toFixed(3)})`);
      gradient.addColorStop(1, 'rgba(205, 222, 236, 0)');
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(point.x, point.y, radius, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}
