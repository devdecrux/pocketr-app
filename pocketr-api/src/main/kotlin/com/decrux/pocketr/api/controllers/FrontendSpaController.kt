package com.decrux.pocketr.api.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class FrontendSpaController {
    @GetMapping(
        value = [
            "/frontend",
            "/frontend/",
            "/frontend/{path:^(?!assets$)[^.]+}",
            "/frontend/{path:^(?!assets$)[^.]+}/{*subpath}",
        ],
    )
    fun forwardToFrontendIndex(): String = "forward:/frontend/index.html"
}
