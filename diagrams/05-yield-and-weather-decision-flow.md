```mermaid
flowchart TD
    START[New cycle] --> YIELD{YieldSignState in range <=28m?}
    YIELD -- yes --> YIELD_DEC[Propose REDUCE_SPEED<br/>priority LEGAL_MANDATORY_STOP]
    YIELD -- no --> WEATHER{WeatherEvent indicates rain?}

    WEATHER -- yes --> RAIN_DEC[Propose REDUCE_SPEED<br/>target 55-65]
    WEATHER -- no --> LOW_VIS{ReducedVisibilityEvent?}

    LOW_VIS -- yes --> VIS_DEC[Propose REDUCE_SPEED<br/>priority ROAD_HAZARD]
    LOW_VIS -- no --> LOW_GRIP{ReducedGripEvent?}

    LOW_GRIP -- yes --> GRIP_DEC[Propose REDUCE_SPEED<br/>priority ROAD_HAZARD]
    LOW_GRIP -- no --> BASE[Fallback rules]

    YIELD_DEC --> PICK[DecisionContext selects best proposal]
    RAIN_DEC --> PICK
    VIS_DEC --> PICK
    GRIP_DEC --> PICK
    BASE --> PICK
    PICK --> END[DrivingDecision]
```
