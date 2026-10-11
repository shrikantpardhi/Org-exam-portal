package com.company.exam.common.web;

import org.slf4j.MDC;

/**
 * Holds the correlation identifier for the current request.
 *
 * <p>The value is established by {@link RequestIdFilter} before the request reaches the
 * application and is cleared once the request completes. It is mirrored into the SLF4J MDC so
 * that structured logs carry it automatically.
 *
 * <p>See {@code specs/09-P0-Foundation-V1.5.md §16} and
 * {@code specs/06-Architecture-Freeze-V1.5.md §82}.
 */
public final class RequestContext {

    /** Response/request header carrying the correlation identifier. */
    public static final String HEADER = "X-Request-Id";

    /** Structured-logging MDC key. */
    public static final String MDC_KEY = "requestId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        CURRENT.set(requestId);
        MDC.put(MDC_KEY, requestId);
    }

    public static String getRequestId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }
}
