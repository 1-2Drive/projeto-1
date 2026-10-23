import { Injectable, NgZone } from '@angular/core';
import { Observable } from 'rxjs';
import { SnapshotStreamMessage } from '../models/api.models';

/** The backend's realtime WebSocket; `ng serve` proxies it with the rest of /api (proxy.conf.json). */
export const REALTIME_PATH = '/api/ws/simulation';

/**
 * The backend pushes a snapshot every 100 ms tick, even while stopped. A socket silent for this
 * long died without a close frame (sleep, network change) and is given up, so the caller reconnects.
 */
export const STALE_AFTER_MS = 5000;

@Injectable({
  providedIn: 'root'
})
export class RealtimeStreamService {
  constructor(private readonly zone: NgZone) {
  }

  /**
   * Opens the realtime WebSocket and emits every snapshot the backend pushes. It errors when the
   * socket closes, goes silent or sends something unreadable, so the caller can reconnect;
   * unsubscribing closes the socket.
   */
  connect(): Observable<SnapshotStreamMessage> {
    return new Observable<SnapshotStreamMessage>((observer) => {
      const fail = (reason: unknown) => this.zone.run(() => observer.error(reason));
      let lastFrameAt = Date.now();

      // Outside Angular: the socket and the watchdog only need change detection for a snapshot.
      const { socket, watchdog } = this.zone.runOutsideAngular(() => {
        const socket = new WebSocket(realtimeUrl());
        socket.onmessage = (event: MessageEvent<string>) => {
          lastFrameAt = Date.now();
          let message: SnapshotStreamMessage;
          try {
            message = JSON.parse(event.data) as SnapshotStreamMessage;
          } catch (err) {
            fail(err);
            return;
          }
          this.zone.run(() => observer.next(message));
        };
        // A failed connection or a dropped one always ends in close, which is reported once here.
        socket.onclose = (event: CloseEvent) => fail(new Error(`Realtime WebSocket closed (code ${event.code})`));
        const watchdog = setInterval(() => {
          if (Date.now() - lastFrameAt > STALE_AFTER_MS) {
            fail(new Error(`Realtime WebSocket silent for ${STALE_AFTER_MS} ms`));
          }
        }, 1000);
        return { socket, watchdog };
      });

      return () => {
        clearInterval(watchdog);
        socket.onmessage = null;
        socket.onclose = null;
        socket.close(1000);
      };
    });
  }
}

function realtimeUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${scheme}//${window.location.host}${REALTIME_PATH}`;
}
