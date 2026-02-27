package com.todo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security configuration.
 *
 * The only purpose of this class is to allow the H2 console to render correctly.
 * H2 console uses HTML iframes, which are blocked by Spring Security's default
 * X-Frame-Options: DENY header.
 *
 * This config:
 *   - Disables CSRF (not needed for a stateless REST API with no session)
 *   - Allows frames from the same origin (required for H2 console)
 *   - Permits all requests (no authentication — per requirements)
 *
 * Note: if Spring Security is not on the classpath this bean is never loaded.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
