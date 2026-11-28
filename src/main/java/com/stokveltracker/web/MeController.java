package com.stokveltracker.web;

import com.stokveltracker.domain.AppUser;
import com.stokveltracker.repo.UserRepository;
import com.stokveltracker.service.Dtos.MeDto;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MeController {
    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public MeDto me(Principal principal) {
        AppUser u = users.findByUsername(principal.getName())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeDto(u.getUsername(), u.getDisplayName(), u.getRole().name(),
                u.getGroup().getName(), u.getGroup().getMonthlyAmountCents());
    }
}
