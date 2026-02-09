package com.decrux.pocketr_api.controllers

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("dev")
@RestController
@RequestMapping("/v1/internal")
class InternalPocketrController {

    @GetMapping("/csrf-token")
    fun getCsrfToken() {
        // Exposes CSRF token in development to support SPA bootstrap.
    }
}
