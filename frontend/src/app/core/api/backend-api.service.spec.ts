import { TestBed } from '@angular/core/testing';
import { BackendApiService } from './backend-api.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('BackendApiService', () => {
  let service: BackendApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BackendApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should request simulation state', () => {
    service.getState().subscribe();
    const req = httpMock.expectOne('/api/simulation/state');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('should post start simulation command', () => {
    service.startSimulation().subscribe();
    const req = httpMock.expectOne('/api/simulation/start');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: 'ok' });
  });
});
