package com.stokveltracker.web;

import com.stokveltracker.service.ContributionService;
import com.stokveltracker.service.Dtos.ContributionDto;
import com.stokveltracker.service.Dtos.MemberOverview;
import com.stokveltracker.web.Requests.ClaimRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

/** Any signed-in user (member, treasurer or chair) can see and claim their own payments. */
@RestController
@RequestMapping("/api/member")
public class MemberController {
    private final ContributionService service;

    public MemberController(ContributionService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public MemberOverview overview(Principal principal) {
        return service.overview(principal.getName());
    }

    @PostMapping("/contributions/{id}/claim")
    public ContributionDto claim(@PathVariable Long id, @Valid @RequestBody ClaimRequest body,
                                 Principal principal) {
        return service.claim(principal.getName(), id, body.reference());
    }
}
