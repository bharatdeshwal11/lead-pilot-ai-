package com.leadpilot.auth;

import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(long id, String name, String email, String passwordHash, Role role, boolean active, long version)
        implements UserDetails {

    public static UserPrincipal from(UserAccount account) {
        return new UserPrincipal(account.getId(), account.getName(), account.getEmail(),
                account.getPasswordHash(), account.getRole(), account.isActive(), account.getVersion());
    }

    UserPrincipal withoutPassword() {
        return new UserPrincipal(id, name, email, null, role, active, version);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + ", role=" + role + "]";
    }
}
