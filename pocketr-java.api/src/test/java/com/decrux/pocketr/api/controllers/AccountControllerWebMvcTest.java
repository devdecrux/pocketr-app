package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.exceptions.CustomExceptionHandler;
import com.decrux.pocketr.api.services.account.ManageAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
@DisplayName("AccountController WebMvc slice")
class AccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageAccount manageAccount;

    @Test
    @DisplayName("list accounts returns serialized response")
    void listAccountsReturnsResponse() throws Exception {
        AccountDto dto = new AccountDto(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            1L,
            "Checking",
            "ASSET",
            "EUR",
            Instant.parse("2026-01-01T00:00:00Z")
        );

        when(manageAccount.listAccountsByMode(any(), eq("INDIVIDUAL"), isNull())).thenReturn(List.of(dto));

        mockMvc.perform(get("/v1/accounts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Checking"))
            .andExpect(jsonPath("$[0].currency").value("EUR"));
    }

    @Test
    @DisplayName("illegal argument is mapped to 400")
    void listAccountsMapsIllegalArgumentToBadRequest() throws Exception {
        when(manageAccount.listAccountsByMode(any(), eq("UNKNOWN"), isNull()))
            .thenThrow(new IllegalArgumentException("Invalid mode"));

        mockMvc.perform(get("/v1/accounts").param("mode", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Invalid mode"));
    }
}
