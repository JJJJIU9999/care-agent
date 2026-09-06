package com.jiu.careagent.common;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class RequestIds {
    public static final String ATTRIBUTE = RequestIds.class.getName();
    public static final String HEADER = "X-Request-ID";

    private RequestIds() {
    }

    public static UUID current(HttpServletRequest request) {
        return (UUID) request.getAttribute(ATTRIBUTE);
    }
}
