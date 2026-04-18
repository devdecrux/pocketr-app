package com.decrux.pocketr.api.config.security

enum class SecurityConstants(
    val value: String,
) {
    BACKEND_LOGIN_PROCESSING_URL("/api/v1/user/login"),
    BACKEND_LOGOUT_PROCESSING_URL("/api/v1/user/logout"),
    BACKEND_REGISTER_ENDPOINT("/api/v1/user/register"),
    GET_CSRF_TOKEN_ENDPOINT("/api/v1/internal/csrf-token"),
    BACKEND_API_ROOT("/api"),
    BACKEND_API("/api/**"),
    FRONTEND_LOGIN_URL("/login"),
    FRONTEND_STATIC_ASSETS("/assets/**"),
    FRONTEND_FAVICON("/favicon.ico"),
    FRONTEND_INDEX("/index.html"),
    FRONTEND_ROOT("/"),
}
