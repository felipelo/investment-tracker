package com.investmenttracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Single-user HTTP Basic authentication. The app holds personal financial records, so every
 * endpoint except the health probes requires the one credential supplied via the environment.
 *
 * <p>ponytail: one hardcoded-by-configuration user instead of a users table and login flow —
 * the app is single-user by design. The ceiling is exactly one identity and no way to revoke
 * or rotate without a restart; the upgrade path is a users table behind the same filter chain.
 */
@Configuration
public class SecurityConfig {

    /** Client-side routing means deep links must reach the SPA, so all paths are protected alike. */
    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", matchIfMissing = true)
    SecurityFilterChain authenticatedFilterChain(HttpSecurity http) throws Exception {
        return http
                // No cookies or sessions: Basic credentials arrive on every request, so there is
                // no session for a forged cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    /**
     * Used only when {@code app.auth.enabled=false} (laptop development and the test suite).
     * Without it, Spring Security's auto-configured defaults would protect everything with a
     * generated password.
     */
    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", havingValue = "false")
    SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", matchIfMissing = true)
    UserDetailsService singleUser(
            @Value("${app.auth.username:}") String username,
            @Value("${app.auth.password-hash:}") String passwordHash) {
        if (username.isBlank() || passwordHash.isBlank()) {
            throw new IllegalStateException(
                    "Authentication is enabled but APP_AUTH_USERNAME / APP_AUTH_PASSWORD_HASH are not set. "
                            + "Set both (the hash is a bcrypt digest) or set app.auth.enabled=false for local development.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(passwordHash).build());
    }

    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", matchIfMissing = true)
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
