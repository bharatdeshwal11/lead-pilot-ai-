package com.leadpilot.auth;

import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final CurrentUser currentUser;

    public AuthController(AuthenticationManager authentication, SecurityContextRepository contexts,
            SessionAuthenticationStrategy sessions, CurrentUser currentUser) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(@Parameter(hidden = true) CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrf.getToken(), csrf.getHeaderName()));
    }

    @PostMapping("/login")
    public ResponseEntity<SessionUser> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var candidate = UsernamePasswordAuthenticationToken.unauthenticated(
                body.email().strip().toLowerCase(Locale.ROOT), body.password());
        var verified = authentication.authenticate(candidate);
        var principal = ((UserPrincipal) verified.getPrincipal()).withoutPassword();
        var sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        sessions.onAuthentication(sessionAuthentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SessionUser.from(principal));
    }

    @GetMapping("/me")
    public ResponseEntity<SessionUser> me(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(SessionUser.from(currentUser.account(principal)));
    }

    @Operation(summary = "Invalidate the current session and clear its cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logoutDocumentation() {
        // Spring Security's LogoutFilter handles these requests before MVC.
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 72) String password) {
    }

    public record SessionUser(long id, String name, String email, Role role, boolean active) {
        static SessionUser from(UserPrincipal principal) {
            return new SessionUser(principal.id(), principal.name(), principal.email(), principal.role(), principal.active());
        }

        static SessionUser from(UserAccount account) {
            return new SessionUser(account.getId(), account.getName(), account.getEmail(), account.getRole(), account.isActive());
        }
    }

    public record CsrfResponse(String token, String headerName) {
    }
}
