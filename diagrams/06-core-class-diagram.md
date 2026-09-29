```mermaid
classDiagram
    class SimulationEngine {
      +tick() SimulationSnapshot
      +start()
      +pause()
      +stop()
      +setRain(boolean)
      +setWeatherFactors(double,double)
      +addRoadElementAhead(...)
      +addActorAhead(...)
      +updateRoadElementById(...)
      +updateActorById(...)
      +removeWorldObjectById(...)
    }

    class SimulationRuntimeService {
      +start()
      +pause()
      +resume()
      +stop()
      +reset()
      +snapshot() ApiSimulationSnapshot
    }

    class RealtimeSnapshotPublisher {
      +register() SseEmitter
      +publish(ApiSimulationSnapshot)
    }

    class SimulationController
    class WorldController
    class ScenarioController
    class DroolsController
    class StreamController

    class KnowledgeSession {
      +getKieSession()
      +getRouter()
      +getDecisionContext()
      +getDrivingDecision()
      +close()
    }

    class EventRouter {
      +insertEvent(Object)
    }

    class DecisionContext {
      +beginCycle(long,double)
      +propose(...)
      +applyTo(DrivingDecision)
    }

    class DrivingDecision
    class SensorManager
    class WorldState
    class DecisionActuator

    SimulationController --> SimulationRuntimeService
    WorldController --> SimulationRuntimeService
    ScenarioController --> SimulationRuntimeService
    DroolsController --> SimulationRuntimeService
    StreamController --> RealtimeSnapshotPublisher
    SimulationRuntimeService --> RealtimeSnapshotPublisher
    SimulationEngine --> KnowledgeSession
    SimulationEngine --> SensorManager
    SimulationEngine --> WorldState
    SimulationEngine --> DecisionActuator
    KnowledgeSession --> EventRouter
    KnowledgeSession --> DecisionContext
    DecisionContext --> DrivingDecision
```
