package com.aichat.exception;

/**
 * 资源不存在异常
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
