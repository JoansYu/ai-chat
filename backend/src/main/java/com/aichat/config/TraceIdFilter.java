package com.aichat.config;

import ch.qos.logback.core.util.StringUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        String traceId = ((HttpServletRequest) servletRequest).getHeader("X-Trace-Id");
        if (StringUtil.isNullOrEmpty(traceId)) {
            traceId = UUID.randomUUID().toString().replaceAll("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        }finally {
            MDC.remove(TRACE_ID);
        }
    }
}
