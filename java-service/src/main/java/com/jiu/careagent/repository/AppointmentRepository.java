package com.jiu.careagent.repository;

import com.jiu.careagent.domain.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    Optional<Appointment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Query("""
            select appointment from Appointment appointment, ServiceSlot slot
            where appointment.userId = :userId and appointment.slotId = slot.id
            order by slot.startAt desc
            """)
    List<Appointment> findOwnedOrderByStartAtDesc(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appointment from Appointment appointment where appointment.id = :id and appointment.userId = :userId")
    Optional<Appointment> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
