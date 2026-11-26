package com.stokveltracker.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class Requests {
    private Requests() {}

    public record ClaimRequest(
            @NotBlank(message = "Please enter a payment reference")
            @Size(max = 100, message = "Reference is too long (max 100 characters)")
            String reference) {}

    public record ReasonRequest(
            @NotBlank(message = "Please give a reason")
            @Size(max = 200, message = "Reason is too long (max 200 characters)")
            String reason) {}
}
