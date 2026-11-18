package com.leadpilot.user;

import com.leadpilot.common.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDtos.Account> list(UserAccount actor) {
        requireAdmin(actor);
        return users.findAllByOrderByNameAscIdAsc().stream().map(UserDtos.Account::from).toList();
    }

    public List<UserDtos.Assignee> assignees(UserAccount actor) {
        if (actor.getRole() == Role.SALES_AGENT) {
            return List.of(UserDtos.Assignee.from(actor));
        }
        return users.findByRoleAndActiveTrueOrderByNameAscIdAsc(Role.SALES_AGENT).stream()
                .map(UserDtos.Assignee::from)
                .toList();
    }

    @Transactional
    public UserDtos.Account create(UserDtos.Create input, UserAccount actor) {
        requireAdmin(actor);
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account already uses that email address.");
        }
        UserAccount user = new UserAccount(input.name().strip(), email, hashPassword(input.password()), input.role());
        return UserDtos.Account.from(users.saveAndFlush(user));
    }

    @Transactional
    public UserDtos.Account update(long id, UserDtos.Update input, UserAccount actor) {
        requireAdmin(actor);
        List<UserAccount> activeAdmins = input.role() != null || input.active() != null
                ? users.findActiveAccountsForUpdate(Role.ADMIN) : List.of();
        UserAccount user = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found."));
        boolean removesAdminAccess = Boolean.FALSE.equals(input.active()) || input.role() == Role.SALES_AGENT;
        if (removesAdminAccess && user.getRole() == Role.ADMIN && user.isActive() && activeAdmins.size() == 1) {
            throw ApiException.conflict("At least one administrator must remain active.");
        }
        user.update(input.name() == null ? null : input.name().strip(), input.role(), input.active(),
                input.password() == null ? null : hashPassword(input.password()));
        return UserDtos.Account.from(user);
    }

    public UserAccount requireActive(long id) {
        return users.findById(id)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.badRequest("Select an active user."));
    }

    public UserAccount requireSalesAgent(long id) {
        UserAccount user = requireActive(id);
        if (user.getRole() != Role.SALES_AGENT) {
            throw ApiException.badRequest("Select an active sales agent.");
        }
        return user;
    }

    public void requireAdmin(UserAccount actor) {
        if (actor.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Administrator access is required.");
        }
    }

    String hashPassword(String password) {
        if (password.length() < 12 || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Use a password with at least 12 characters and at most 72 UTF-8 bytes.");
        }
        return passwordEncoder.encode(password);
    }
}
