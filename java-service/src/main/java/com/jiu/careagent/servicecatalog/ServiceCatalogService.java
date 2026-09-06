package com.jiu.careagent.servicecatalog;

import com.jiu.careagent.domain.ServiceItem;
import com.jiu.careagent.domain.ServiceSlot;
import com.jiu.careagent.repository.ServiceItemRepository;
import com.jiu.careagent.repository.ServiceSlotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ServiceCatalogService {
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final ServiceItemRepository services;
    private final ServiceSlotRepository slots;

    public ServiceCatalogService(ServiceItemRepository services, ServiceSlotRepository slots) {
        this.services = services;
        this.slots = slots;
    }

    public ServicesResponse search(String district, String category, LocalDate date) {
        String districtFilter = blankToNull(district);
        String categoryFilter = blankToNull(category);
        List<ServiceItem> matched;
        if (districtFilter != null && categoryFilter != null) {
            matched = services.findAllByEnabledTrueAndDistrictAndCategoryOrderByName(districtFilter, categoryFilter);
        } else if (districtFilter != null) {
            matched = services.findAllByEnabledTrueAndDistrictOrderByName(districtFilter);
        } else if (categoryFilter != null) {
            matched = services.findAllByEnabledTrueAndCategoryOrderByName(categoryFilter);
        } else {
            matched = services.findAllByEnabledTrueOrderByName();
        }
        if (matched.isEmpty()) {
            return new ServicesResponse(List.of());
        }

        Instant now = Instant.now();
        Instant from = date == null ? now : date.atStartOfDay(SHANGHAI).toInstant();
        Instant until = date == null ? null : date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        List<UUID> serviceIds = matched.stream().map(ServiceItem::getId).toList();
        List<ServiceSlot> available = until == null
                ? slots.findAvailableFrom(serviceIds, from)
                : slots.findAvailableBetween(serviceIds, from, until);
        Map<UUID, List<ServiceSlot>> byService = available
                .stream().collect(Collectors.groupingBy(ServiceSlot::getServiceId));

        List<ServiceView> items = matched.stream()
                .map(service -> toView(service, byService.getOrDefault(service.getId(), List.of())))
                .filter(service -> !service.availableSlots().isEmpty())
                .toList();
        return new ServicesResponse(items);
    }

    public ServiceView toView(ServiceItem service, List<ServiceSlot> slots) {
        return new ServiceView(service.getId(), service.getName(), service.getCategory(), service.getDistrict(),
                service.getDescription(), formatPrice(service.getPrice()),
                slots.stream().map(ServiceCatalogService::toSlotView).toList());
    }

    public static SlotView toSlotView(ServiceSlot slot) {
        return new SlotView(slot.getId(), atShanghai(slot.getStartAt()), atShanghai(slot.getEndAt()), slot.getRemainingCapacity());
    }

    public static OffsetDateTime atShanghai(Instant instant) {
        return instant.atZone(SHANGHAI).toOffsetDateTime();
    }

    public static String formatPrice(BigDecimal price) {
        return price.setScale(2).toPlainString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ServicesResponse(List<ServiceView> items) {
    }

    public record ServiceView(UUID serviceId, String name, String category, String district, String description,
                              String price, List<SlotView> availableSlots) {
    }

    public record SlotView(UUID slotId, OffsetDateTime startAt, OffsetDateTime endAt, int remainingCapacity) {
    }
}
