package com.aichat.exception;

/**
 * 业务异常：用于可预期的业务规则错误（如登录失败、参数不合规等），
 * 由 GlobalExceptionHandler 统一转换为 HTTP 400。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
