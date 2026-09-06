package com.jiu.careagent.repository;

import com.jiu.careagent.domain.ServiceSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ServiceSlotRepository extends JpaRepository<ServiceSlot, UUID> {
    @Query("""
            select slot from ServiceSlot slot
            where slot.serviceId in :serviceIds
              and slot.startAt >= :from
              and slot.remainingCapacity > 0
            order by slot.startAt
            """)
    List<ServiceSlot> findAvailableFrom(
            @Param("serviceIds") List<UUID> serviceIds,
            @Param("from") Instant from);

    @Query("""
            select slot from ServiceSlot slot
            where slot.serviceId in :serviceIds
              and slot.startAt >= :from
              and slot.startAt < :until
              and slot.remainingCapacity > 0
            order by slot.startAt
            """)
    List<ServiceSlot> findAvailableBetween(
            @Param("serviceIds") List<UUID> serviceIds,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Modifying
    @Query(value = """
            UPDATE app.service_slot
            SET remaining_capacity = remaining_capacity - 1, updated_at = now()
            WHERE id = :slotId
              AND service_id = :serviceId
              AND start_at > now()
              AND remaining_capacity > 0
            """, nativeQuery = true)
    int decrementCapacity(@Param("slotId") UUID slotId, @Param("serviceId") UUID serviceId);

    @Modifying
    @Query(value = """
            UPDATE app.service_slot
            SET remaining_capacity = remaining_capacity + 1, updated_at = now()
            WHERE id = :slotId AND remaining_capacity < total_capacity
            """, nativeQuery = true)
    int restoreCapacity(@Param("slotId") UUID slotId);
}
