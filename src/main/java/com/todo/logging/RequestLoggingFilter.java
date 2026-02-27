package com.todo.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that runs once per request and populates SLF4J's MDC
 * (Mapped Diagnostic Context) with request-scoped metadata.
 *
 * This enables every log line emitted during a request (controller, service, repository)
 * to automatically include:
 *   - traceId: a UUID unique to this request, useful for correlating logs
 *   - httpMethod: GET, POST, PATCH, etc.
 *   - requestPath: the URI being processed
 *
 * The traceId is also echoed back in the X-Trace-Id response header so clients
 * can include it in bug reports.
 *
 * MDC is always cleared in the finally block to prevent leakage between threads
 * in pooled environments.
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String MDC_TRACE_ID    = "traceId";
    static final String MDC_METHOD      = "httpMethod";
    static final String MDC_PATH        = "requestPath";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        long startMs   = System.currentTimeMillis();

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_METHOD,   request.getMethod());
        MDC.put(MDC_PATH,     request.getRequestURI());

        response.setHeader(TRACE_ID_HEADER, traceId);

        log.info(">> {} {} [traceId={}]",
                request.getMethod(), request.getRequestURI(), traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("<< {} {} | status={} durationMs={} [traceId={}]",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs, traceId);
            MDC.clear();
        }
    }

    /**
     * Honour an inbound X-Trace-Id header if present (useful when a gateway
     * or upstream service sets it). Otherwise generate a new UUID.
     */
    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(TRACE_ID_HEADER);
        return (inbound != null && !inbound.isBlank())
                ? inbound
                : UUID.randomUUID().toString();
    }
}
