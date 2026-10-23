import { createCamera } from './road-projection';
import { dropCount, RainFrame, rainIntensity, RainSimulation } from './road-rain';

/** A repeatable stand-in for Math.random. */
function seeded(seed = 7): () => number {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

function frame(overrides: Partial<RainFrame> = {}): RainFrame {
  return {
    camera: createCamera({ width: 960, height: 420 }, 3.5),
    egoPos: 100,
    egoLateral: 3.5,
    speedMps: 14,
    intensity: 0.6,
    occluders: [],
    ...overrides
  };
}

describe('road rain', () => {
  it('rains harder as visibility drops, never less than a light shower', () => {
    expect(rainIntensity(1)).toBe(0.25);
    expect(rainIntensity(0.7)).toBeCloseTo(0.6, 5);
    expect(rainIntensity(0.3)).toBe(1);
    expect(rainIntensity(null)).toBeCloseTo(0.6, 5);
  });

  it('scales the number of drops with the canvas and the intensity', () => {
    expect(dropCount(960, 420, 1)).toBeGreaterThan(dropCount(960, 420, 0.5));
    expect(dropCount(1920, 1080, 1)).toBeGreaterThan(dropCount(960, 420, 1));
    expect(dropCount(10000, 10000, 1)).toBe(2200);
  });

  it('keeps as many drops as the frame asks for, and splashes them on the road', () => {
    const rain = new RainSimulation(seeded());
    const f = frame();
    for (let i = 0; i < 120; i++) {
      rain.step(f, 1 / 60);
    }
    expect(rain.dropTotal).toBe(dropCount(960, 420, 0.6));
    expect(rain.splashTotal).toBeGreaterThan(0);

    rain.step(frame({ intensity: 0.25 }), 1 / 60);
    expect(rain.dropTotal).toBe(dropCount(960, 420, 0.25));
  });

  it('throws spray behind the ego only while it moves', () => {
    const rain = new RainSimulation(seeded());
    for (let i = 0; i < 60; i++) {
      rain.step(frame({ speedMps: 0 }), 1 / 60);
    }
    expect(rain.puffTotal).toBe(0);
    for (let i = 0; i < 60; i++) {
      rain.step(frame({ speedMps: 20 }), 1 / 60);
    }
    expect(rain.puffTotal).toBeGreaterThan(0);
  });

  it('draws without throwing on a canvas', () => {
    const rain = new RainSimulation(seeded());
    const f = frame();
    rain.step(f, 1 / 60);
    const canvas = document.createElement('canvas');
    canvas.width = 960;
    canvas.height = 420;
    expect(() => rain.draw(canvas.getContext('2d')!, f)).not.toThrow();
  });
});
