package com.decrux.pocketr.api.config.security;

public enum SecurityConstants {
    BACKEND_LOGIN_PROCESSING_URL("/v1/user/login"),
    BACKEND_LOGOUT_PROCESSING_URL("/v1/user/logout"),
    BACKEND_REGISTER_ENDPOINT("/v1/user/register"),
    GET_CSRF_TOKEN_ENDPOINT("/v1/internal/csrf-token"),
    FRONTEND_LOGIN_URL("/frontend/login"),
    FRONTEND_REGISTER_URL("/frontend/registration"),
    FRONTEND_STATIC_ASSETS("/frontend/assets/**"),
    FRONTEND_FAVICON("/frontend/favicon.ico"),
    FRONTEND_LANDING_PAGE("/frontend/index.html");

    private final String value;

    SecurityConstants(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
