package com.stokveltracker.web;

import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dtos.ClaimDto;
import com.stokveltracker.service.Dtos.OverdueMemberDto;
import com.stokveltracker.web.Requests.ReasonRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Restricted to the TREASURER role in SecurityConfig. */
@RestController
@RequestMapping("/api/treasurer")
public class TreasurerController {
    private final ContributionService service;

    public TreasurerController(ContributionService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<ClaimDto> pending(Principal principal) {
        return service.pending(principal.getName());
    }

    @GetMapping("/confirmed")
    public List<ClaimDto> confirmed(Principal principal) {
        return service.recentlyConfirmed(principal.getName());
    }

    @GetMapping("/overdue")
    public List<OverdueMemberDto> overdue(Principal principal) {
        return service.overdue(principal.getName());
    }

    @PostMapping("/contributions/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id, Principal principal) {
        service.confirm(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contributions/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
                                       Principal principal) {
        service.reject(principal.getName(), id, body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contributions/{id}/reverse")
    public ResponseEntity<Void> reverse(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
                                        Principal principal) {
        service.reverse(principal.getName(), id, body.reason());
        return ResponseEntity.noContent().build();
    }
}
