import { ApiSimulationSnapshot } from '../../core/models/api.models';
import { blendKinematics, EMPTY_KINEMATICS, isContinuation, kinematicsOf } from './road-kinematics';

function snapshot(egoPos: number, extra: Partial<ApiSimulationSnapshot['egoVehicle']> = {}, objects: unknown[] = []): ApiSimulationSnapshot {
  return {
    simulationTimeMillis: 1000,
    status: 'RUNNING',
    egoVehicle: { speedKmh: 50, positionMeters: egoPos, lane: 'CENTER', accelerationMps2: 0, ...extra },
    worldObjects: objects
  } as unknown as ApiSimulationSnapshot;
}

describe('road kinematics', () => {
  it('reads the drawn numbers straight from the snapshot', () => {
    const k = kinematicsOf(snapshot(40, { lateralPositionMeters: 1.5, headingDegrees: -3 }, [
      { id: 'a', type: 'Pothole', positionMeters: 30, worldPositionMeters: 70, lane: 'RIGHT', details: '' },
      { id: 'p', type: 'Pedestrian', positionMeters: 12, lane: 'LEFT', details: '', lateralPositionMeters: -2 }
    ]));
    expect(k.egoPos).toBe(40);
    expect(k.egoLateral).toBe(1.5);
    expect(k.heading).toBe(-3);
    expect(k.objects['a']).toEqual({ world: 70, lateral: null });
    // Without a world position the relative distance is added to the ego position.
    expect(k.objects['p']).toEqual({ world: 52, lateral: -2 });
  });

  it('falls back to the lane centre while no lateral position is reported', () => {
    expect(kinematicsOf(snapshot(0, { lane: 'LEFT', lateralPositionMeters: undefined as never })).egoLateral).toBe(-3.5);
    expect(kinematicsOf(null)).toBe(EMPTY_KINEMATICS);
  });

  it('blends linearly and lands exactly on the target', () => {
    const from = kinematicsOf(snapshot(10, { lateralPositionMeters: 3.5 }, [{ id: 'v', type: 'OtherVehicle', positionMeters: 20, worldPositionMeters: 30, lane: 'RIGHT', details: '' }]));
    const to = kinematicsOf(snapshot(12, { lateralPositionMeters: 2.5 }, [{ id: 'v', type: 'OtherVehicle', positionMeters: 19, worldPositionMeters: 31, lane: 'RIGHT', details: '' }]));
    const half = blendKinematics(from, to, 0.5);
    expect(half.egoPos).toBeCloseTo(11, 9);
    expect(half.egoLateral).toBeCloseTo(3, 9);
    expect(half.objects['v'].world).toBeCloseTo(30.5, 9);
    expect(blendKinematics(from, to, 0)).toEqual(jasmine.objectContaining({ egoPos: 10 }));
    expect(blendKinematics(from, to, 1)).toBe(to);
    expect(blendKinematics(from, to, 1.7)).toBe(to);
  });

  it('never slides an object that was not in the previous state, or jumped a long way', () => {
    const from = kinematicsOf(snapshot(10, {}, [{ id: 'v', type: 'OtherVehicle', positionMeters: 20, worldPositionMeters: 30, lane: 'RIGHT', details: '' }]));
    const to = kinematicsOf(snapshot(11, {}, [
      { id: 'v', type: 'OtherVehicle', positionMeters: 20, worldPositionMeters: 500, lane: 'RIGHT', details: '' },
      { id: 'new', type: 'Pothole', positionMeters: 20, worldPositionMeters: 31, lane: 'RIGHT', details: '' }
    ]));
    const half = blendKinematics(from, to, 0.5);
    expect(half.objects['v'].world).toBe(500);
    expect(half.objects['new'].world).toBe(31);
  });

  it('treats a reset or scenario load as a new scene, not as motion', () => {
    expect(isContinuation(kinematicsOf(snapshot(10)), kinematicsOf(snapshot(11)))).toBeTrue();
    expect(isContinuation(kinematicsOf(snapshot(900)), kinematicsOf(snapshot(0)))).toBeFalse();
  });
});
