package com.leadpilot.auth;

import com.leadpilot.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionGuard extends OncePerRequestFilter {
    private final UserRepository users;
    private final SecurityProblems problems;

    SessionGuard(UserRepository users, SecurityProblems problems) {
        this.users = users;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") || path.equals("/api/auth/login") || path.equals("/api/auth/csrf");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            boolean current = users.findById(principal.id())
                    .filter(account -> CurrentUser.matches(account, principal))
                    .isPresent();
            if (!current) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Your session expired. Sign in again.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
