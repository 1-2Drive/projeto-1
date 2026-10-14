import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  ApiDecisionView,
  ApiSimulationSnapshot,
  ApiSystemStatus,
  ApiTimelineEntry,
  ApiWorkingMemoryRow,
  CommandResponse,
  CreateActorRequest,
  CreateRoadElementRequest,
  EnvironmentUpdateRequest,
  ExpertBackend,
  MapCellRequest,
  ScenarioSummary,
  WorldMutationResponse
} from '../models/api.models';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class BackendApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  getState(): Observable<ApiSimulationSnapshot> {
    return this.http.get<ApiSimulationSnapshot>(`${this.baseUrl}/simulation/state`);
  }

  getSystemStatus(): Observable<ApiSystemStatus> {
    return this.http.get<ApiSystemStatus>(`${this.baseUrl}/simulation/status`);
  }

  getScenarios(): Observable<ScenarioSummary[]> {
    return this.http.get<ScenarioSummary[]>(`${this.baseUrl}/scenarios`);
  }

  loadScenario(id: string): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/scenarios/${id}/load`, {});
  }

  startSimulation(): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/start`, {});
  }

  pauseSimulation(): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/pause`, {});
  }

  resumeSimulation(): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/resume`, {});
  }

  stopSimulation(): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/stop`, {});
  }

  /** Switches the deciding rule engine; the backend reloads the current scenario. */
  switchBackend(backend: ExpertBackend): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/backend/${backend.toLowerCase()}`, {});
  }

  resetSimulation(): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/reset`, {});
  }

  requestLaneChange(direction: 'LEFT' | 'RIGHT'): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/simulation/lane-change/${direction}`, {});
  }

  setMapCell(request: MapCellRequest): Observable<WorldMutationResponse> {
    return this.http.put<WorldMutationResponse>(`${this.baseUrl}/world/map/cells`, request);
  }

  addRoadElement(request: CreateRoadElementRequest): Observable<WorldMutationResponse> {
    return this.http.post<WorldMutationResponse>(`${this.baseUrl}/world/elements`, request);
  }

  addActor(request: CreateActorRequest): Observable<WorldMutationResponse> {
    return this.http.post<WorldMutationResponse>(`${this.baseUrl}/world/actors`, request);
  }

  updateEnvironment(request: EnvironmentUpdateRequest): Observable<CommandResponse> {
    return this.http.post<CommandResponse>(`${this.baseUrl}/environment`, request);
  }

  getWorkingMemory(): Observable<ApiWorkingMemoryRow[]> {
    return this.http.get<ApiWorkingMemoryRow[]>(`${this.baseUrl}/drools/working-memory`);
  }

  getDecision(): Observable<ApiDecisionView> {
    return this.http.get<ApiDecisionView>(`${this.baseUrl}/drools/decision`);
  }

  getRecentRules(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/drools/rules/recent`);
  }

  getRecentExplanations(): Observable<ApiTimelineEntry[]> {
    return this.http.get<ApiTimelineEntry[]>(`${this.baseUrl}/explanations/recent`);
  }
}
