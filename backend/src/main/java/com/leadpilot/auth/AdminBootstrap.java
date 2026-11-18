package com.leadpilot.auth;

import com.leadpilot.user.Role;
import com.leadpilot.user.UserAccount;
import com.leadpilot.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final boolean demoMode;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository users, PasswordEncoder passwords,
            @Value("${leadpilot.demo.enabled}") boolean demoMode,
            @Value("${leadpilot.bootstrap.email}") String email,
            @Value("${leadpilot.bootstrap.password}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.demoMode = demoMode;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoMode || (email.isEmpty() && password.isEmpty()) || users.existsByRole(Role.ADMIN)) {
            return;
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254
                || password.isBlank() || passwordBytes < 14 || passwordBytes > 72) {
            throw new IllegalStateException("Configure a valid bootstrap admin email and a password of 14 to 72 UTF-8 bytes.");
        }
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalStateException("The configured bootstrap admin email is already in use.");
        }
        users.save(new UserAccount("Workspace Administrator", email, passwords.encode(password), Role.ADMIN));
    }
}
