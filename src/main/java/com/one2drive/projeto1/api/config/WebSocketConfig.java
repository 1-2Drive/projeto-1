package com.one2drive.projeto1.api.config;

import com.one2drive.projeto1.api.controller.RealtimeWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * The realtime channel: plain WebSocket (no STOMP), JSON text frames, backend to frontend.
 * It lives under {@code /api} so the Angular dev proxy forwards it with the REST calls.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    public static final String REALTIME_PATH = "/api/ws/simulation";

    private final RealtimeWebSocketHandler handler;

    public WebSocketConfig(RealtimeWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // A browser always sends Origin on a WebSocket handshake and CORS does not apply to it,
        // so the origin is checked here; clients without one (tests, tools) are let through.
        registry.addHandler(handler, REALTIME_PATH).setAllowedOrigins(CorsConfig.FRONTEND_ORIGIN);
    }
}
