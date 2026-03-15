package com.decrux.pocketr.api.controllers

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(FrontendSpaController::class)
@AutoConfigureMockMvc(addFilters = false)
class FrontendSpaControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `forwards top-level frontend routes to the SPA entrypoint`() {
        mockMvc
            .perform(get("/frontend/dashboard"))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/frontend/index.html"))
    }

    @Test
    fun `forwards nested frontend routes to the SPA entrypoint`() {
        mockMvc
            .perform(get("/frontend/household/123/settings"))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/frontend/index.html"))
    }

    @Test
    fun `does not intercept frontend asset requests`() {
        mockMvc
            .perform(get("/frontend/assets/app.js"))
            .andExpect(status().isNotFound)
    }
}
