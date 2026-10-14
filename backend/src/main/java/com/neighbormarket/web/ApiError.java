package com.neighbormarket.web;

import org.slf4j.MDC;

public record ApiError(String code, String message, String requestId) {
    public ApiError(String code, String message) {
        this(code, message, MDC.get(RequestIdFilter.MDC_KEY));
    }
}
