package com.aichat.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

/** Owns the lifecycle of one SSE response. */
public final class SseConnection {

    private final SseEmitter emitter;
    private final String streamId = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean disconnected = new AtomicBoolean(false);
    private final AtomicBoolean terminal = new AtomicBoolean(false);

    public SseConnection(long timeout) {
        this.emitter = new SseEmitter(timeout);
        emitter.onError(error -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onCompletion(() -> disconnected.set(true));
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public boolean isClosed() {
        return disconnected.get() || terminal.get();
    }

    public void send(Object data) {
        if (isClosed()) {
            throw new ClientDisconnectedException();
        }

        try {
            emitter.send(SseEmitter.event()
                    .id(streamId + ":" + sequence.incrementAndGet())
                    .name("message")
                    .data(data));
        } catch (IOException | IllegalStateException e) {
            disconnected.set(true);
            throw new ClientDisconnectedException(e);
        }
    }

    public void complete() {
        if (!terminal.compareAndSet(false, true) || disconnected.get()) {
            return;
        }

        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            disconnected.set(true);
        }
    }

    /** Sends one final error event only while the response is usable. */
    public void sendError(Throwable error) {
        if (disconnected.get() || !terminal.compareAndSet(false, true)) {
            return;
        }

        String message = error.getMessage() == null ? "Server error" : error.getMessage();
        try {
            emitter.send(SseEmitter.event()
                    .id(streamId + ":" + sequence.incrementAndGet())
                    .name("error")
                    .data(Map.of("type", "error", "message", message)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            disconnected.set(true);
        }
    }

    public static final class ClientDisconnectedException extends RuntimeException {
        public ClientDisconnectedException() {
            super("SSE client disconnected");
        }

        public ClientDisconnectedException(Throwable cause) {
            super("SSE client disconnected", cause);
        }
    }
}
