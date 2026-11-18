package com.leadpilot.lead;

import com.leadpilot.activity.ActivityService;
import com.leadpilot.auth.CurrentUser;
import com.leadpilot.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadService leads;
    private final CurrentUser currentUser;

    public LeadController(LeadService leads, CurrentUser currentUser) {
        this.leads = leads;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<LeadDtos.Summary> create(@Valid @RequestBody LeadDtos.Create request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        LeadDtos.Summary created = leads.create(request, currentUser.account(principal));
        return ResponseEntity.created(URI.create("/api/leads/" + created.id())).body(created);
    }

    @GetMapping
    public LeadDtos.Page list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) LeadStatus status,
                              @RequestParam(required = false) Priority priority,
                              @RequestParam(required = false) LeadSource source,
                              @RequestParam(required = false) Long assignedUserId,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(defaultValue = "createdAt,desc") String sort,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return leads.list(new LeadDtos.Filter(q, status, priority, source, assignedUserId, page, size, sort),
                currentUser.account(principal));
    }

    @GetMapping("/{id}")
    public LeadDtos.Summary get(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        return leads.get(id, currentUser.account(principal));
    }

    @PatchMapping("/{id}")
    public LeadDtos.Summary update(@PathVariable long id, @Valid @RequestBody LeadDtos.Update request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return leads.update(id, request, currentUser.account(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, @AuthenticationPrincipal UserPrincipal principal) {
        leads.delete(id, currentUser.account(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/activities")
    public List<ActivityService.Entry> activities(@PathVariable long id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return leads.history(id, currentUser.account(principal));
    }
}
