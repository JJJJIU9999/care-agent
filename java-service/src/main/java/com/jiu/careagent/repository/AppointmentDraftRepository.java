package com.jiu.careagent.repository;

import com.jiu.careagent.domain.AppointmentDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppointmentDraftRepository extends JpaRepository<AppointmentDraft, UUID> {
    Optional<AppointmentDraft> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE app.appointment_draft
            SET status = 'USED', used_at = now(), updated_at = now()
            WHERE id = :draftId
              AND user_id = :userId
              AND status = 'PENDING'
              AND expires_at > now()
            """, nativeQuery = true)
    int consume(@Param("draftId") UUID draftId, @Param("userId") UUID userId);
}
