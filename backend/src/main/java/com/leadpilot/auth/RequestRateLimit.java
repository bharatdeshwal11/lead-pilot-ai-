package com.leadpilot.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

final class RequestRateLimit extends OncePerRequestFilter {
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_ACTIVE_KEYS = 4_096;
    private static final Pattern AI_PATH = Pattern.compile("/api/leads/[+]?[0-9]+/(analyze|reply-draft)");
    private final Clock clock;
    private final SecurityProblems problems;
    private final int loginLimit;
    private final int aiLimit;
    private final Map<String, Window> windows = new HashMap<>();
    private long nextCleanup;

    RequestRateLimit(Clock clock, SecurityProblems problems, int loginLimit, int aiLimit) {
        this.clock = clock;
        this.problems = problems;
        this.loginLimit = loginLimit;
        this.aiLimit = aiLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if ("POST".equals(request.getMethod())) {
            String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
            long retryAfter = 0;
            if (path.equals("/api/auth/login")) {
                retryAfter = retryAfter("login:" + request.getRemoteAddr(), loginLimit);
            } else if (AI_PATH.matcher(path).matches()) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
                    retryAfter = retryAfter("ai:" + principal.id(), aiLimit);
                }
            }
            if (retryAfter > 0) {
                response.setHeader("Retry-After", Long.toString(retryAfter));
                problems.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again shortly.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private synchronized long retryAfter(String key, int limit) {
        if (limit <= 0) {
            return 0;
        }
        long now = clock.instant().getEpochSecond();
        if (now >= nextCleanup) {
            windows.values().removeIf(window -> window.expiresAt <= now);
            nextCleanup = now + WINDOW_SECONDS;
        }
        Window window = windows.get(key);
        if (window == null || window.expiresAt <= now) {
            if (window == null && windows.size() >= MAX_ACTIVE_KEYS) {
                return Math.max(1, nextCleanup - now);
            }
            windows.put(key, new Window(now + WINDOW_SECONDS));
            return 0;
        }
        if (window.count >= limit) {
            return window.expiresAt - now;
        }
        window.count++;
        return 0;
    }

    private static final class Window {
        private final long expiresAt;
        private int count = 1;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
