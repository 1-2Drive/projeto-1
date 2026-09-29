import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';
import { SnapshotStreamMessage } from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class RealtimeStreamService {
  constructor(private readonly zone: NgZone) {
  }

  connect(): Observable<SnapshotStreamMessage> {
    return new Observable<SnapshotStreamMessage>((observer) => {
      const source = new EventSource('/api/stream/snapshots');

      source.addEventListener('snapshot', (event) => {
        this.zone.run(() => {
          try {
            observer.next(JSON.parse((event as MessageEvent<string>).data) as SnapshotStreamMessage);
          } catch (err) {
            observer.error(err);
          }
        });
      });

      source.onerror = (err) => {
        this.zone.run(() => observer.error(err));
      };

      return () => source.close();
    });
  }
}
