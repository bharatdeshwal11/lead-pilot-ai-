package com.leadpilot.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadpilot.user.UserRepository;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
            CorsConfigurationSource corsConfiguration, ObjectMapper objectMapper, UserRepository users, Clock clock,
            @Value("${leadpilot.security.login-limit}") int loginLimit,
            @Value("${leadpilot.security.ai-limit}") int aiLimit) throws Exception {
        var problems = new SecurityProblems(objectMapper);
        http
                .cors(cors -> cors.configurationSource(corsConfiguration))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("LEADPILOT_SESSION")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Sign in to continue."))
                        .accessDeniedHandler((request, response, error) -> problems.write(request, response,
                                HttpStatus.FORBIDDEN, request.getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName()) != null
                                        && error instanceof org.springframework.security.web.csrf.CsrfException
                                        ? "The security token is missing or expired. Refresh and retry."
                                        : "You do not have permission to perform this action.")))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "font-src 'self'; img-src 'self' data:; connect-src 'self'; "
                                + "object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/assignees").hasAnyRole("ADMIN", "SALES_AGENT")
                        .requestMatchers("/api/users", "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/leads/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "SALES_AGENT")
                        .anyRequest().permitAll())
                .addFilterBefore(new RequestRateLimit(clock, problems, loginLimit, aiLimit), CsrfFilter.class)
                .addFilterBefore(new SessionGuard(users, problems), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email).map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    CorsConfigurationSource corsConfiguration(@Value("${leadpilot.security.allowed-origins}") String origins) {
        List<String> allowlist = Arrays.stream(origins.split(",")).map(String::strip)
                .filter(origin -> !origin.isEmpty()).toList();
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowlist);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
