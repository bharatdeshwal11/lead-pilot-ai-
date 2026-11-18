package com.leadpilot.dashboard;

import com.leadpilot.auth.CurrentUser;
import com.leadpilot.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final CurrentUser currentUser;

    DashboardController(DashboardService dashboard, CurrentUser currentUser) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
    }

    @GetMapping("/stats")
    @Operation(summary = "Summarize leads and overdue follow-ups in the current user's scope")
    public DashboardStats stats(@Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return dashboard.stats(currentUser.account(principal));
    }
}
