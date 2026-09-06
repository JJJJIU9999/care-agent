package com.jiu.careagent.appointment;

import com.jiu.careagent.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentService.AppointmentView> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmRequest body,
            HttpServletRequest request) {
        AppointmentService.ConfirmationResult result = service.confirm(userId(jwt), body.draftId(), idempotencyKey,
                RequestIds.current(request));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.appointment());
    }

    @GetMapping
    public AppointmentService.AppointmentList list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @PostMapping("/{appointmentId}/cancel")
    public AppointmentService.AppointmentView cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID appointmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        return service.cancel(userId(jwt), appointmentId, idempotencyKey, RequestIds.current(request));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record ConfirmRequest(@NotNull(message = "draftId 不能为空") UUID draftId) {
    }
}
