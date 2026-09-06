package com.jiu.careagent.appointment;

import com.jiu.careagent.audit.AuditService;
import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.domain.Appointment;
import com.jiu.careagent.domain.AppointmentDraft;
import com.jiu.careagent.domain.ServiceItem;
import com.jiu.careagent.domain.ServiceSlot;
import com.jiu.careagent.repository.AppointmentDraftRepository;
import com.jiu.careagent.repository.AppointmentRepository;
import com.jiu.careagent.repository.ServiceItemRepository;
import com.jiu.careagent.repository.ServiceSlotRepository;
import com.jiu.careagent.servicecatalog.ServiceCatalogService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final AppointmentDraftRepository drafts;
    private final ServiceItemRepository services;
    private final ServiceSlotRepository slots;
    private final AuditService audit;
    private final EntityManager entityManager;

    public AppointmentService(AppointmentRepository appointments, AppointmentDraftRepository drafts,
                              ServiceItemRepository services, ServiceSlotRepository slots,
                              AuditService audit, EntityManager entityManager) {
        this.appointments = appointments;
        this.drafts = drafts;
        this.services = services;
        this.slots = slots;
        this.audit = audit;
        this.entityManager = entityManager;
    }

    @Transactional
    public ConfirmationResult confirm(UUID userId, UUID draftId, String idempotencyKey, UUID requestId) {
        String key = validateIdempotencyKey(idempotencyKey);
        lockIdempotencyKey(userId, key);
        Appointment existing = appointments.findByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing != null) {
            return new ConfirmationResult(toView(existing), true);
        }

        if (drafts.consume(draftId, userId) != 1) {
            AppointmentDraft draft = drafts.findByIdAndUserId(draftId, userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "预约草案不存在"));
            throw new ApiException(HttpStatus.CONFLICT, "DRAFT_ALREADY_USED_OR_EXPIRED", "预约草案已使用或已过期");
        }

        AppointmentDraft draft = drafts.findByIdAndUserId(draftId, userId).orElseThrow();
        ServiceItem service = services.findByIdAndEnabledTrue(draft.getServiceId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SERVICE_UNAVAILABLE", "服务当前不可用"));
        ServiceSlot slot = slots.findById(draft.getSlotId())
                .filter(candidate -> candidate.getServiceId().equals(service.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SERVICE_UNAVAILABLE", "服务时段与服务不匹配"));
        if (slots.decrementCapacity(slot.getId(), service.getId()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_FULL", "服务时段已满或已开始");
        }

        Appointment appointment = appointments.saveAndFlush(new Appointment(draft, service.getPrice(), key));
        audit.record("CREATE_APPOINTMENT", userId, requestId, "SUCCESS", appointment.getId());
        return new ConfirmationResult(toView(appointment, service, slot), false);
    }

    @Transactional(readOnly = true)
    public AppointmentList list(UUID userId) {
        return new AppointmentList(appointments.findOwnedOrderByStartAtDesc(userId).stream()
                .map(this::toView)
                .toList());
    }

    @Transactional
    public AppointmentView cancel(UUID userId, UUID appointmentId, String idempotencyKey, UUID requestId) {
        validateIdempotencyKey(idempotencyKey);
        Appointment appointment = appointments.findOwnedForUpdate(appointmentId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "预约不存在"));
        if (!"CANCELLED".equals(appointment.getStatus())) {
            appointment.cancel();
            if (slots.restoreCapacity(appointment.getSlotId()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_RESTORE_FAILED", "预约取消失败");
            }
            audit.record("CANCEL_APPOINTMENT", userId, requestId, "SUCCESS", appointment.getId());
        }
        return toView(appointment);
    }

    private void lockIdempotencyKey(UUID userId, String key) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
                .setParameter("lockKey", userId + ":" + key)
                .getSingleResult();
    }

    private String validateIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IDEMPOTENCY_KEY", "幂等键必须为 1 到 128 个字符");
        }
        return value;
    }

    private AppointmentView toView(Appointment appointment) {
        ServiceItem service = services.findById(appointment.getServiceId()).orElseThrow();
        ServiceSlot slot = slots.findById(appointment.getSlotId()).orElseThrow();
        return toView(appointment, service, slot);
    }

    private AppointmentView toView(Appointment appointment, ServiceItem service, ServiceSlot slot) {
        return new AppointmentView(appointment.getId(), appointment.getStatus(), service.getName(),
                ServiceCatalogService.atShanghai(slot.getStartAt()), ServiceCatalogService.atShanghai(slot.getEndAt()),
                appointment.getConfirmedPrice());
    }

    public record ConfirmationResult(AppointmentView appointment, boolean replayed) {
    }

    public record AppointmentList(List<AppointmentView> items) {
    }

    public record AppointmentView(UUID appointmentId, String status, String serviceName,
                                  OffsetDateTime startAt, OffsetDateTime endAt, BigDecimal confirmedPrice) {
    }
}
