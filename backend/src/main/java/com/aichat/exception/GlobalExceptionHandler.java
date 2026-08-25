package com.aichat.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Central exception mapping. Client-aborted SSE connections are expected and not server errors. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void writeJsonToResponse(HttpServletResponse response, int httpStatus,
                                     int code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    @ExceptionHandler(NotLoginException.class)
    public void handlerNotLoginException(NotLoginException e, HttpServletResponse response) throws IOException {
        writeJsonToResponse(response, HttpStatus.UNAUTHORIZED.value(), 401,
                "Authentication failed. Please log in again.");
    }

    @ExceptionHandler(NotFoundException.class)
    public void handleNotFound(NotFoundException e, HttpServletResponse response) throws IOException {
        writeJsonToResponse(response, HttpStatus.NOT_FOUND.value(), 404, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException e, HttpServletResponse response) throws IOException {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(field -> field.getDefaultMessage())
                .orElse("Request validation failed");
        writeJsonToResponse(response, HttpStatus.BAD_REQUEST.value(), 400, message);
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneric(Exception e, HttpServletResponse response) throws IOException {
        if (isClientDisconnect(e)) {
            log.debug("SSE client disconnected before response completion: {}", e.getMessage());
            return;
        }
        log.error("Unhandled server exception", e);
        writeJsonToResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), 500,
                "Internal server error: " + e.getMessage());
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String type = current.getClass().getName();
            String message = current.getMessage();
            if (type.contains("ClientAbortException") || type.contains("EOFException")
                    || (current instanceof IOException && message != null && (
                    message.contains("Connection reset")
                            || message.contains("Broken pipe")
                            || message.contains("中止了一个已建立的连接")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
