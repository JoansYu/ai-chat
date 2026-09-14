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

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 核心武器：绕过 Spring 的 Content Negotiation（媒体协商），
     * 直接强制向 HttpServletResponse 写入 JSON 字符串。
     */
    private void writeJsonToResponse(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
        response.setStatus(httpStatus);
        // 强制告诉前端：不管你之前请求的是什么格式，我现在塞给你的就是 JSON！
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("message", message);

        // 直接将 JSON 字符串写入流
        response.getWriter().write(objectMapper.writeValueAsString(map));
    }

    @ExceptionHandler(NotLoginException.class)
    public void handlerNotLoginException(NotLoginException e, HttpServletResponse response) throws IOException {
        // 返回 HTTP 401 (UNAUTHORIZED) 状态码，业务 code 401
        writeJsonToResponse(response, HttpStatus.UNAUTHORIZED.value(), 401, "认证失败：请重新登陆");
    }

    @ExceptionHandler(NotFoundException.class)
    public void handleNotFound(NotFoundException e, HttpServletResponse response) throws IOException {
        writeJsonToResponse(response, HttpStatus.NOT_FOUND.value(), 404, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public void handleBusiness(BusinessException e, HttpServletResponse response) throws IOException {
        writeJsonToResponse(response, HttpStatus.BAD_REQUEST.value(), 400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidation(MethodArgumentNotValidException e, HttpServletResponse response) throws IOException {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("参数校验失败");
        writeJsonToResponse(response, HttpStatus.BAD_REQUEST.value(), 400, message);
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException e, HttpServletResponse response) {
        // 前端主动断开连接，正常现象，静默处理即可
        log.info("客户端断开连接（ClientAbort），忽略: {}", e.getMessage());
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.OK.value());
        }
    }

    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException e, HttpServletResponse response) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        // 客户端断开导致的连接中止/Broken pipe 等，属于 SSE 断连的正常现象，静默处理
        if (msg.contains("中止") || msg.contains("broken pipe")
                || msg.contains("connection reset") || msg.contains("connection aborted")
                || msg.contains("connection is closed")) {
            log.info("客户端连接中断（SSE），忽略: {}", e.getMessage());
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.OK.value());
            }
            return;
        }
        log.error("IO 异常", e);
        try {
            writeJsonToResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), 500, "服务器内部错误：" + e.getMessage());
        } catch (IOException ioEx) {
            log.error("写入 IO 错误响应失败，连接可能已断开", ioEx);
        }
    }

    @ExceptionHandler(Exception.class)
    public void handleGeneric(Exception e, HttpServletResponse response) throws IOException {
        log.error("未处理异常", e);
        writeJsonToResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), 500, "服务器内部错误：" + e.getMessage());
    }
}