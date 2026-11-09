package com.teamforge.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.platform.api.ApiErrors;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
        @Value("${teamforge.security.auth-attempts-per-minute:30}") int attemptsPerMinute) throws Exception {
        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health",
                    "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/me", "/api/v1/me/profile").authenticated()
                .anyRequest().denyAll())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
            .formLogin(form -> form.loginPage("/api/v1/auth/login")
                .loginProcessingUrl("/api/v1/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, error) -> ApiErrors.write(response, mapper,
                    401, "INVALID_CREDENTIALS", "Email or password is incorrect.")))
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterBefore(new AuthRateLimitFilter(mapper, Clock.systemUTC(), attemptsPerMinute),
                UsernamePasswordAuthenticationFilter.class)
            // Session-backed CSRF remains enabled, including registration/login/logout.
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> ApiErrors.write(response, mapper,
                    401, "UNAUTHENTICATED", "Sign in to continue."))
                .accessDeniedHandler((request, response, error) -> ApiErrors.write(response, mapper, 403,
                    error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                    error instanceof CsrfException ? "Refresh the page and try again." : "Access denied.")))
            .build();
    }
}
