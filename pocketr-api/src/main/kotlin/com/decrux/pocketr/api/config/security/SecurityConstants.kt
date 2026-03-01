package com.decrux.pocketr.api.config.security

enum class SecurityConstants(
    val value: String,
) {
    BACKEND_LOGIN_PROCESSING_URL("/v1/user/login"),
    BACKEND_LOGOUT_PROCESSING_URL("/v1/user/logout"),
    BACKEND_REGISTER_ENDPOINT("/v1/user/register"),
    GET_CSRF_TOKEN_ENDPOINT("/v1/internal/csrf-token"),
    FRONTEND_LOGIN_URL("/frontend/login"),
    FRONTEND_REGISTER_URL("/frontend/registration"),
    FRONTEND_STATIC_ASSETS("/frontend/assets/**"),
    FRONTEND_FAVICON("/frontend/favicon.ico"),
    FRONTEND_LANDING_PAGE("/frontend/index.html"),
}
