package com.leadpilot.user;

import com.leadpilot.auth.CurrentUser;
import com.leadpilot.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;
    private final CurrentUser currentUser;

    public UserController(UserService users, CurrentUser currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserDtos.Account> list(@AuthenticationPrincipal UserPrincipal principal) {
        return users.list(currentUser.account(principal));
    }

    @GetMapping("/assignees")
    public List<UserDtos.Assignee> assignees(@AuthenticationPrincipal UserPrincipal principal) {
        return users.assignees(currentUser.account(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.Account create(@Valid @RequestBody UserDtos.Create request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return users.create(request, currentUser.account(principal));
    }

    @PatchMapping("/{id}")
    public UserDtos.Account update(@PathVariable long id, @Valid @RequestBody UserDtos.Update request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return users.update(id, request, currentUser.account(principal));
    }
}
