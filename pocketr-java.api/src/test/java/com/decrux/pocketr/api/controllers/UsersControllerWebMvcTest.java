package com.decrux.pocketr.api.controllers;

import com.decrux.pocketr.api.entities.dtos.UserDto;
import com.decrux.pocketr.api.exceptions.CustomExceptionHandler;
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService;
import com.decrux.pocketr.api.services.user_registration.RegisterUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsersController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CustomExceptionHandler.class)
@DisplayName("UsersController WebMvc slice")
class UsersControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUser registerUser;

    @MockitoBean
    private UserAvatarService userAvatarService;

    @Test
    @DisplayName("register endpoint accepts valid payload")
    void registerAcceptsPayload() throws Exception {
        mockMvc.perform(
                post("/v1/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "password": "encoded-password",
                          "email": "test@example.com",
                          "firstName": "Test",
                          "lastName": "User"
                        }
                        """)
            )
            .andExpect(status().isOk());

        verify(registerUser).registerUser(any());
    }

    @Test
    @DisplayName("register endpoint maps illegal argument to 400")
    void registerMapsIllegalArgumentToBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Email already exists")).when(registerUser).registerUser(any());

        mockMvc.perform(
                post("/v1/user/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "password": "encoded-password",
                          "email": "duplicate@example.com",
                          "firstName": "Dup",
                          "lastName": "User"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Email already exists"));
    }

    @Test
    @DisplayName("retrieve user endpoint serializes dto")
    void retrieveUserDataSerializesDto() throws Exception {
        when(userAvatarService.toUserDto(any())).thenReturn(new UserDto(1L, "test@example.com", "Test", "User", null));

        mockMvc.perform(get("/v1/user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }
}
