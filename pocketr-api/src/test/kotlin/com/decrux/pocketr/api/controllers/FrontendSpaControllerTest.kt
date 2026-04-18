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
    fun `forwards root to the SPA entrypoint`() {
        mockMvc
            .perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/index.html"))
    }

    @Test
    fun `forwards top-level frontend routes to the SPA entrypoint`() {
        mockMvc
            .perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/index.html"))
    }

    @Test
    fun `forwards nested frontend routes to the SPA entrypoint`() {
        mockMvc
            .perform(get("/household/123/settings"))
            .andExpect(status().isOk)
            .andExpect(forwardedUrl("/index.html"))
    }

    @Test
    fun `does not intercept asset requests`() {
        mockMvc
            .perform(get("/assets/app.js"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `does not intercept api requests`() {
        mockMvc
            .perform(get("/api/v1/accounts"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `does not intercept api root`() {
        mockMvc
            .perform(get("/api"))
            .andExpect(status().isNotFound)
    }
}
