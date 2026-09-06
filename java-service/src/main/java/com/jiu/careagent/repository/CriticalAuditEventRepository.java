package com.jiu.careagent.repository;

import com.jiu.careagent.domain.CriticalAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CriticalAuditEventRepository extends JpaRepository<CriticalAuditEvent, UUID> {
}
