```mermaid
sequenceDiagram
    participant API as Spring API Loop
    participant ENG as SimulationEngine
    participant PHY as WorldPhysicsEngine
    participant SNS as SensorManager
    participant KIE as KnowledgeSession/KieSession
    participant ACT as DecisionActuator
    participant SSE as SSE Publisher
    participant ANG as Angular Frontend

    API->>ENG: tick()
    ENG->>PHY: updateWorld(world, dt)
    ENG->>ENG: advance simTime + clocks
    ENG->>ENG: flushPendingMutations()
    ENG->>ENG: runTimedActions()
    ENG->>KIE: update VehicleStateFact
    ENG->>SNS: observe(world, simTime)
    SNS-->>ENG: events[]
    loop each event
        ENG->>KIE: router.insertEvent(event)
    end
    ENG->>KIE: fireAllRules()
    KIE-->>ENG: DecisionContext -> DrivingDecision
    ENG->>ACT: applyDecision(world, decision)
    ACT-->>ENG: ego state updated
    ENG-->>API: SimulationSnapshot
    API->>SSE: publish(snapshot)
    SSE-->>ANG: realtime update
```
