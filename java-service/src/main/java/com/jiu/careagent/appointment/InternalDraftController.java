package com.jiu.careagent.appointment;

import com.jiu.careagent.security.InternalTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/tools/appointment-drafts")
public class InternalDraftController {
    private final InternalTokenService internalToken;
    private final DraftService draftService;

    public InternalDraftController(InternalTokenService internalToken, DraftService draftService) {
        this.internalToken = internalToken;
        this.draftService = draftService;
    }

    @PostMapping
    public DraftService.DraftResponse create(
            @RequestHeader("X-Internal-Token") String token,
            @RequestHeader("X-Request-ID") UUID requestId,
            @Valid @RequestBody DraftRequest body) {
        internalToken.requireValid(token);
        return draftService.create(body.userId(), body.serviceId(), body.slotId());
    }

    public record DraftRequest(@NotNull UUID userId, @NotNull UUID serviceId, @NotNull UUID slotId) {
    }
}
