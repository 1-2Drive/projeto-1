package com.one2drive.projeto1.api.controller;

import com.one2drive.projeto1.api.service.RealtimeSnapshotPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * One WebSocket session per browser at {@code /api/ws/simulation}. It only receives: on
 * connect the session gets the current snapshot, then every snapshot the simulation
 * publishes. Commands stay on REST, where each one has its own response; text the client
 * sends is ignored.
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final RealtimeSnapshotPublisher publisher;

    public RealtimeWebSocketHandler(RealtimeSnapshotPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        publisher.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        publisher.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        publisher.unregister(session);
        if (session.isOpen()) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (IOException ignored) {
                // Already broken; the frontend reconnects either way.
            }
        }
    }
}
