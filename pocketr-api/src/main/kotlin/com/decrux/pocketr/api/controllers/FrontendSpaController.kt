package com.decrux.pocketr.api.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class FrontendSpaController {
    @GetMapping(
        value = [
            "/",
            "/{path:^(?!api$|assets$|favicon\\.ico$)[^.]+}",
            "/{path:^(?!api$|assets$)[^.]+}/{*subpath}",
        ],
    )
    fun forwardToFrontendIndex(): String = "forward:/index.html"
}
