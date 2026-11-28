package com.stokveltracker.web;

import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dtos.AuditDto;
import com.stokveltracker.service.Dtos.ChairSummary;
import com.stokveltracker.service.Dtos.ClaimDto;
import com.stokveltracker.service.Dtos.ConfirmedPage;
import com.stokveltracker.web.Requests.ReasonRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Restricted to the CHAIR role in SecurityConfig. */
@RestController
@RequestMapping("/api/chair")
public class ChairController {
    private final ContributionService service;

    public ChairController(ContributionService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ChairSummary summary(Principal principal) {
        return service.summary(principal.getName());
    }

    @GetMapping("/audit")
    public List<AuditDto> audit(Principal principal) {
        return service.auditTrail(principal.getName());
    }

    /** Treasurer payments waiting for the chair's countersignature. */
    @GetMapping("/treasurer/pending")
    public List<ClaimDto> treasurerPending(Principal principal) {
        return service.chairPending(principal.getName());
    }

    @GetMapping("/treasurer/confirmed")
    public ConfirmedPage treasurerConfirmed(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            Principal principal) {
        return service.chairRecentlyConfirmed(principal.getName(), page, size);
    }

    @PostMapping("/contributions/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id, Principal principal) {
        service.chairConfirm(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contributions/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
                                       Principal principal) {
        service.chairReject(principal.getName(), id, body.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contributions/{id}/reverse")
    public ResponseEntity<Void> reverse(@PathVariable Long id, @Valid @RequestBody ReasonRequest body,
                                        Principal principal) {
        service.chairReverse(principal.getName(), id, body.reason());
        return ResponseEntity.noContent().build();
    }
}
