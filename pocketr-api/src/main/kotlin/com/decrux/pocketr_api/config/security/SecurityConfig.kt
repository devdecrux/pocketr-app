package com.decrux.pocketr_api.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val spaCsrfTokenRequestHandler: SpaCsrfTokenRequestHandler,
    private val customAuthenticationSuccessHandler: CustomAuthenticationSuccessHandler,
    private val customAuthenticationFailureHandler: CustomAuthenticationFailureHandler,
    private val customLogoutSuccessHandler: CustomLogoutSuccessHandler,
    private val customAuthenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customUserDetailsService: CustomUserDetailsService,
    @Value("\${app.security.csrf-cookie-path:}")
    private val csrfCookiePath: String,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val csrfTokenRepository =
            CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
                if (csrfCookiePath.isNotBlank()) {
                    setCookiePath(csrfCookiePath)
                }
            }

        return http
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(spaCsrfTokenRequestHandler)
            }.sessionManagement { session ->
                session.maximumSessions(1)
            }.formLogin { form ->
                form
                    .loginPage(SecurityConstants.FRONTEND_LOGIN_URL.value)
                    .permitAll()
                    .loginProcessingUrl(SecurityConstants.BACKEND_LOGIN_PROCESSING_URL.value)
                    .permitAll()
                    .usernameParameter("email")
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(customAuthenticationFailureHandler)
            }.authorizeHttpRequests { authorizeRequests ->
                authorizeRequests
                    .requestMatchers(SecurityConstants.FRONTEND_STATIC_ASSETS.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.FRONTEND_FAVICON.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.FRONTEND_LANDING_PAGE.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.FRONTEND_LOGIN_URL.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.FRONTEND_REGISTER_URL.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.BACKEND_REGISTER_ENDPOINT.value)
                    .permitAll()
                    .requestMatchers(SecurityConstants.GET_CSRF_TOKEN_ENDPOINT.value)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling { exception ->
                exception.authenticationEntryPoint(customAuthenticationEntryPoint)
            }.userDetailsService(customUserDetailsService)
            .logout { logout ->
                logout
                    .logoutUrl(SecurityConstants.BACKEND_LOGOUT_PROCESSING_URL.value)
                    .clearAuthentication(true)
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler(customLogoutSuccessHandler)
            }.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
