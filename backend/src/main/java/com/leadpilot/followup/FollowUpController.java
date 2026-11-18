package com.leadpilot.followup;

import com.leadpilot.auth.CurrentUser;
import com.leadpilot.auth.UserPrincipal;
import com.leadpilot.followup.FollowUpDtos.CreateRequest;
import com.leadpilot.followup.FollowUpDtos.PageResponse;
import com.leadpilot.followup.FollowUpDtos.Response;
import com.leadpilot.followup.FollowUpDtos.UpdateRequest;
import com.leadpilot.followup.FollowUpDtos.View;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follow-ups")
@Tag(name = "Follow-ups")
public class FollowUpController {

    private final FollowUpService followUps;
    private final CurrentUser currentUser;

    FollowUpController(FollowUpService followUps, CurrentUser currentUser) {
        this.followUps = followUps;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a follow-up for a lead")
    public Response create(@Valid @RequestBody CreateRequest request,
                           @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return followUps.create(request, currentUser.account(principal));
    }

    @GetMapping
    @Operation(summary = "List visible follow-ups by lead and due-date status")
    public PageResponse list(@RequestParam(required = false) @Positive Long leadId,
                             @RequestParam(defaultValue = "ALL") View view,
                             @RequestParam(defaultValue = "0") @Min(0) int page,
                             @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
                             @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return followUps.list(leadId, view, page, size, currentUser.account(principal));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit or complete a follow-up")
    public Response update(@PathVariable @Positive long id, @Valid @RequestBody UpdateRequest request,
                           @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return followUps.update(id, request, currentUser.account(principal));
    }
}
