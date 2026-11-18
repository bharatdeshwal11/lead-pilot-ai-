package com.leadpilot.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    public record Create(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull Role role,
            @NotNull @Size(min = 12, max = 72) String password
    ) {
    }

    public record Update(
            @Size(max = 120) @Pattern(regexp = "(?s).*\\S.*") String name,
            Role role,
            Boolean active,
            @Size(min = 12, max = 72) String password
    ) {
    }

    public record Account(Long id, String name, String email, Role role, boolean active) {
        static Account from(UserAccount user) {
            return new Account(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
        }
    }

    public record Assignee(Long id, String name) {
        public static Assignee from(UserAccount user) {
            return user == null ? null : new Assignee(user.getId(), user.getName());
        }
    }
}
