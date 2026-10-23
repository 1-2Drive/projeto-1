import { ApiFlowStep, ApiRuleRef, ApiWhyAnswer } from '../../core/models/api.models';

/** Test data only: an explanation shaped as the backend returns it for a pothole avoidance. */
const POTHOLE_AVOID = 'Avoid pothole in right lane by changing left';
const CRUISE = 'Build back up to the scenario cruise speed when no sign governs the road';

function rule(name: string, action: string, channel: ApiRuleRef['channel'], speed: number | null, priority: number,
              rung: string, outcome: string, detail = ''): ApiRuleRef {
  return { rule: name, action, channel, recommendedSpeedKmh: speed, priority, rung, outcome, detail, reason: '', evidence: [] };
}

function step(nodeId: string, branch: ApiFlowStep['branch'], takenLabel: string, decisive = false,
              extra: Partial<ApiFlowStep> = {}): ApiFlowStep {
  return {
    nodeId, docRef: '', question: `Question ${nodeId}?`, branch, yesLabel: `yes ${nodeId}`, noLabel: `no ${nodeId}`,
    takenLabel, decisive, rules: [], supportingRules: [], conditions: [], observed: [], ...extra
  };
}

export const POTHOLE_ANSWER: ApiWhyAnswer = {
  sessionId: 'just-stopped',
  timestampMillis: 27700,
  engine: 'DROOLS',
  situation: { speedKmh: 42, lane: 'RIGHT', laneChangeState: 'IDLE', recommendedSpeedKmh: 50, actuatorResult: 'action=CHANGE_LANE_LEFT' },
  why: [
    {
      channel: 'LONGITUDINAL', title: 'Speed · longitudinal', action: 'MAINTAIN_SPEED', effect: 'speeds up from 42 to 50 km/h',
      relevant: true,
      summary: `The vehicle speeds up from 42 to 50 km/h because '${CRUISE}' holds: Road is clear.`,
      decidingRule: rule(CRUISE, 'MAINTAIN_SPEED', 'LONGITUDINAL', 50, 20, 'speed compliance', 'WON'),
      conditions: ['cruise=50.0 km/h, limit=120.0 km/h, current=42.0 km/h'],
      arbitration: 'It was the only speed proposal (speed compliance, priority 20).',
      alsoConsidered: []
    },
    {
      channel: 'LATERAL', title: 'Lane · lateral', action: 'CHANGE_LANE_LEFT', effect: 'moves from the RIGHT to the CENTER lane',
      relevant: true,
      summary: `The vehicle moves from the RIGHT to the CENTER lane because '${POTHOLE_AVOID}' holds: Avoiding pothole.`,
      decidingRule: rule(POTHOLE_AVOID, 'CHANGE_LANE_LEFT', 'LATERAL', null, 40, 'road hazard', 'WON'),
      conditions: ['pothole 18.0m ahead in own lane, left lane clear', 'Lane safety (left): safe to change left'],
      arbitration: 'It was the only lane proposal (road hazard, priority 40).',
      alsoConsidered: [rule('Allow lane change left when lane safe', 'CHANGE_LANE_LEFT', 'LATERAL', null, 30, 'maneuver safety',
        'LOST_TO', POTHOLE_AVOID)]
    }
  ],
  whyNot: [
    {
      channel: 'LONGITUDINAL', option: 'HOLD', label: 'Keep the current speed', status: 'CONDITION_NOT_MET',
      summary: `'${CRUISE}' targets 50 km/h, above the current 42 km/h, and no rule held the speed lower.`,
      requiredConditions: [{ text: 'No rule targets a speed above the current 42 km/h', holds: false },
        { text: `'${CRUISE}' targets 50 km/h (speed compliance, priority 20)`, holds: true }],
      blockingRules: [rule(CRUISE, 'MAINTAIN_SPEED', 'LONGITUDINAL', 50, 20, 'speed compliance', 'WON')],
      prevailing: 'The speed decision was MAINTAIN_SPEED (the vehicle speeds up from 42 to 50 km/h).'
    },
    {
      channel: 'LONGITUDINAL', option: 'REDUCE', label: 'Slow down', status: 'CONDITION_NOT_MET',
      summary: 'No rule found a condition that requires slowing down.',
      requiredConditions: [{ text: "Is a pothole in the vehicle's lane 40 m or less ahead, with no safe side to avoid it? — no", holds: false }],
      blockingRules: [], prevailing: 'The speed decision was MAINTAIN_SPEED.'
    },
    {
      channel: 'LATERAL', option: 'KEEP_LANE', label: 'Stay in the current lane', status: 'CONDITION_NOT_MET',
      summary: `Keeping the lane is the default only when no lane rule proposes a maneuver, and '${POTHOLE_AVOID}' did.`,
      requiredConditions: [{ text: 'No lane rule proposes a maneuver', holds: false }],
      blockingRules: [rule(POTHOLE_AVOID, 'CHANGE_LANE_LEFT', 'LATERAL', null, 40, 'road hazard', 'WON')],
      prevailing: 'The lane decision was CHANGE_LANE_LEFT.'
    },
    {
      channel: 'LATERAL', option: 'CHANGE_LANE_RIGHT', label: 'Change to the right lane', status: 'NOT_APPLICABLE',
      summary: 'There is no lane to the right of the RIGHT lane.', requiredConditions: [], blockingRules: [],
      prevailing: 'The lane decision was CHANGE_LANE_LEFT.'
    }
  ],
  how: [
    {
      channel: 'LONGITUDINAL', title: 'Speed · longitudinal', decision: 'MAINTAIN_SPEED — speeds up from 42 to 50 km/h',
      steps: [
        step('V10', 'NO', 'No — TTC is not immediately critical', false, { observed: ['Front radar: no vehicle ahead'] }),
        step('R03C', 'YES', `Yes — '${CRUISE}' decides the speed: MAINTAIN_SPEED at 50 km/h`, true,
          { rules: [rule(CRUISE, 'MAINTAIN_SPEED', 'LONGITUDINAL', 50, 20, 'speed compliance', 'WON')] }),
        step('DV', 'YES', 'It was the only speed proposal.', true, { noLabel: '' })
      ],
      outcome: 'The vehicle speeds up from 42 to 50 km/h.'
    },
    {
      channel: 'LATERAL', title: 'Lane · lateral', decision: 'CHANGE_LANE_LEFT — moves from the RIGHT to the CENTER lane',
      steps: [
        step('00', 'YES', 'Yes — left: safe to change left; right: no lane to the right', false, {
          conditions: [{ text: 'Left · no lane change in progress', holds: true }, { text: 'Right · no lane to the right', holds: false }]
        }),
        step('F12', 'YES', `Yes — '${POTHOLE_AVOID}' decides the lane: CHANGE_LANE_LEFT`, true,
          { rules: [rule(POTHOLE_AVOID, 'CHANGE_LANE_LEFT', 'LATERAL', null, 40, 'road hazard', 'WON')] }),
        step('F11', 'NOT_REACHED', 'Not on the decision path: the lane was already settled at F12')
      ],
      outcome: 'The vehicle moves from the RIGHT to the CENTER lane.'
    }
  ]
};
