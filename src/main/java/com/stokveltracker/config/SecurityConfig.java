package com.stokveltracker.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Session-cookie login for a same-origin single page app.
 * CSRF protection is disabled on purpose: the session cookie is SameSite=Strict and
 * HttpOnly, and the API only accepts JSON. Revisit if the API is ever opened to other origins.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/treasurer/**").hasRole("TREASURER")
                .requestMatchers("/api/chair/**").hasRole("CHAIR")
                .requestMatchers("/api/**").authenticated()
                // Only the built SPA and Spring's own error/login endpoints are public.
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                 "/login", "/error").permitAll()
                .anyRequest().denyAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
                .failureHandler((req, res, ex) -> res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
