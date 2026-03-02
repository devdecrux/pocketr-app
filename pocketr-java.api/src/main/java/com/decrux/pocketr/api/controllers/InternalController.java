package com.decrux.pocketr.api.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/v1/internal")
public class InternalController {

    @GetMapping("/csrf-token")
    public void getCsrfToken() {
        // Exposes CSRF token in development to support SPA bootstrap.
    }
}
