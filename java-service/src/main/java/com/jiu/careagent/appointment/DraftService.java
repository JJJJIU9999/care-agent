package com.jiu.careagent.appointment;

import com.jiu.careagent.common.ApiException;
import com.jiu.careagent.domain.AppointmentDraft;
import com.jiu.careagent.domain.ServiceItem;
import com.jiu.careagent.domain.ServiceSlot;
import com.jiu.careagent.repository.AppUserRepository;
import com.jiu.careagent.repository.AppointmentDraftRepository;
import com.jiu.careagent.repository.ServiceItemRepository;
import com.jiu.careagent.repository.ServiceSlotRepository;
import com.jiu.careagent.servicecatalog.ServiceCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DraftService {
    private final AppUserRepository users;
    private final ServiceItemRepository services;
    private final ServiceSlotRepository slots;
    private final AppointmentDraftRepository drafts;

    public DraftService(AppUserRepository users, ServiceItemRepository services, ServiceSlotRepository slots,
                        AppointmentDraftRepository drafts) {
        this.users = users;
        this.services = services;
        this.slots = slots;
        this.drafts = drafts;
    }

    @Transactional
    public DraftResponse create(UUID userId, UUID serviceId, UUID slotId) {
        if (!users.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        ServiceItem service = services.findByIdAndEnabledTrue(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SERVICE_UNAVAILABLE", "服务当前不可用"));
        ServiceSlot slot = slots.findById(slotId)
                .filter(candidate -> candidate.getServiceId().equals(serviceId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "服务时段不存在"));
        if (!slot.getStartAt().isAfter(Instant.now()) || slot.getRemainingCapacity() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SLOT_FULL", "服务时段已满或已开始");
        }

        AppointmentDraft draft = drafts.save(new AppointmentDraft(userId, serviceId, slotId, Instant.now().plusSeconds(600)));
        return new DraftResponse(draft.getId(), new DraftServiceView(service.getName()),
                new DraftSlotView(ServiceCatalogService.atShanghai(slot.getStartAt()), ServiceCatalogService.atShanghai(slot.getEndAt())),
                ServiceCatalogService.formatPrice(service.getPrice()), ServiceCatalogService.atShanghai(draft.getExpiresAt()));
    }

    public record DraftResponse(UUID draftId, DraftServiceView service, DraftSlotView slot,
                                String displayPrice, OffsetDateTime expiresAt) {
    }

    public record DraftServiceView(String name) {
    }

    public record DraftSlotView(OffsetDateTime startAt, OffsetDateTime endAt) {
    }
}
