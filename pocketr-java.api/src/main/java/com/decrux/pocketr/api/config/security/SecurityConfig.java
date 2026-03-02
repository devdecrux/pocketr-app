package com.decrux.pocketr.api.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SpaCsrfTokenRequestHandler spaCsrfTokenRequestHandler;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final CustomLogoutSuccessHandler customLogoutSuccessHandler;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomUserDetailsService customUserDetailsService;
    private final String csrfCookiePath;

    public SecurityConfig(
            SpaCsrfTokenRequestHandler spaCsrfTokenRequestHandler,
            CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
            CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
            CustomLogoutSuccessHandler customLogoutSuccessHandler,
            CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
            CustomUserDetailsService customUserDetailsService,
            @Value("${app.security.csrf-cookie-path:}") String csrfCookiePath
    ) {
        this.spaCsrfTokenRequestHandler = spaCsrfTokenRequestHandler;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.customLogoutSuccessHandler = customLogoutSuccessHandler;
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.customUserDetailsService = customUserDetailsService;
        this.csrfCookiePath = csrfCookiePath;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        if (!csrfCookiePath.isBlank()) {
            csrfTokenRepository.setCookiePath(csrfCookiePath);
        }

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(spaCsrfTokenRequestHandler)
                )
                .sessionManagement(session -> session.maximumSessions(1))
                .formLogin(form -> form
                        .loginPage(SecurityConstants.FRONTEND_LOGIN_URL.getValue())
                        .permitAll()
                        .loginProcessingUrl(SecurityConstants.BACKEND_LOGIN_PROCESSING_URL.getValue())
                        .permitAll()
                        .usernameParameter("email")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                )
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers(SecurityConstants.FRONTEND_STATIC_ASSETS.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.FRONTEND_FAVICON.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.FRONTEND_LANDING_PAGE.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.FRONTEND_LOGIN_URL.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.FRONTEND_REGISTER_URL.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.BACKEND_REGISTER_ENDPOINT.getValue()).permitAll()
                        .requestMatchers(SecurityConstants.GET_CSRF_TOKEN_ENDPOINT.getValue()).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .userDetailsService(customUserDetailsService)
                .logout(logout -> logout
                        .logoutUrl(SecurityConstants.BACKEND_LOGOUT_PROCESSING_URL.getValue())
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .logoutSuccessHandler(customLogoutSuccessHandler)
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
