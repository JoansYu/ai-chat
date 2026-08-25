package com.aichat.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

/** Owns the lifecycle of one SSE response. */
public final class SseConnection {

    private final SseEmitter emitter;
    private final String streamId = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean disconnected = new AtomicBoolean(false);
    private final AtomicBoolean terminal = new AtomicBoolean(false);
    private final AtomicReference<Runnable> closeAction = new AtomicReference<>();

    public SseConnection(long timeout) {
        this.emitter = new SseEmitter(timeout);
        emitter.onError(error -> markDisconnected());
        emitter.onTimeout(this::markDisconnected);
        emitter.onCompletion(this::markDisconnected);
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public String requestId() {
        return streamId;
    }

    public boolean isClosed() {
        return disconnected.get() || terminal.get();
    }

    /** Cancels the model task when the browser or proxy closes the SSE response. */
    public void onClose(Runnable action) {
        closeAction.set(action);
        if (disconnected.get()) {
            action.run();
        }
    }

    private void markDisconnected() {
        if (terminal.get()) {
            return;
        }
        if (disconnected.compareAndSet(false, true)) {
            Runnable action = closeAction.get();
            if (action != null) {
                action.run();
            }
        }
    }

    public synchronized void send(Object data) {
        if (isClosed()) {
            throw new ClientDisconnectedException();
        }

        try {
            emitter.send(SseEmitter.event()
                    .id(streamId + ":" + sequence.incrementAndGet())
                    .name("message")
                    .data(data));
        } catch (IOException | IllegalStateException e) {
            markDisconnected();
            throw new ClientDisconnectedException(e);
        }
    }

    public void sendStatus(String status, String message) {
        send(Map.of("type", "status", "status", status, "message", message));
    }

    public void sendHeartbeat() {
        send(Map.of("type", "heartbeat", "timestamp", System.currentTimeMillis()));
    }

    public synchronized void complete() {
        if (!terminal.compareAndSet(false, true) || disconnected.get()) {
            return;
        }

        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            markDisconnected();
        }
    }

    /** Sends one final error event only while the response is usable. */
    public synchronized void sendError(Throwable error) {
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
            markDisconnected();
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
