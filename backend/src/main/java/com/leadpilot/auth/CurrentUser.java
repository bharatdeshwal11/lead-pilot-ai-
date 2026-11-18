package com.leadpilot.auth;

import com.leadpilot.common.ApiException;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UserAccount account(UserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        return users.findById(principal.id()).filter(account -> matches(account, principal))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Your session expired. Sign in again."));
    }

    static boolean matches(UserAccount account, UserPrincipal principal) {
        return account.isActive() && account.getVersion() == principal.version()
                && account.getRole() == principal.role()
                && account.getEmail().equalsIgnoreCase(principal.email());
    }
}
