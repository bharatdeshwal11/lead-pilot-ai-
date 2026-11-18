package com.leadpilot.ai;

import com.leadpilot.auth.CurrentUser;
import com.leadpilot.auth.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api")
class AiController {
    private final AiService service;
    private final CurrentUser currentUser;

    AiController(AiService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/leads/{id}/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    AiDtos.AnalysisView analyze(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.analyze(id, currentUser.account(principal));
    }

    @GetMapping("/leads/{id}/analyses")
    List<AiDtos.AnalysisView> analyses(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.listAnalyses(id, currentUser.account(principal));
    }

    @PostMapping("/leads/{id}/reply-draft")
    @ResponseStatus(HttpStatus.CREATED)
    AiDtos.DraftView createDraft(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.createDraft(id, currentUser.account(principal));
    }

    @GetMapping("/leads/{id}/reply-drafts")
    List<AiDtos.DraftView> drafts(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.listDrafts(id, currentUser.account(principal));
    }

    @PatchMapping("/reply-drafts/{id}")
    AiDtos.DraftView edit(
            @PathVariable long id, @Valid @RequestBody AiDtos.EditDraft edit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.editDraft(id, currentUser.account(principal), edit);
    }

    @PostMapping("/reply-drafts/{id}/approve")
    AiDtos.DraftView approve(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.reviewDraft(id, currentUser.account(principal), true);
    }

    @PostMapping("/reply-drafts/{id}/reject")
    AiDtos.DraftView reject(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.reviewDraft(id, currentUser.account(principal), false);
    }
}
