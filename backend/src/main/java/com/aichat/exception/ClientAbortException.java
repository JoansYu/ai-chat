package com.aichat.exception;

/** Signals a closed SSE consumer without treating it as a server failure. */
public class ClientAbortException extends RuntimeException {
    public ClientAbortException() { super("CLIENT_ABORT"); }
}
