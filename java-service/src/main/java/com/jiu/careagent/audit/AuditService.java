package com.jiu.careagent.audit;

import com.jiu.careagent.domain.CriticalAuditEvent;
import com.jiu.careagent.repository.CriticalAuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {
    private final CriticalAuditEventRepository repository;

    public AuditService(CriticalAuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(String eventType, UUID userId, UUID requestId, String result, UUID targetResourceId) {
        repository.save(new CriticalAuditEvent(eventType, userId, requestId, result, targetResourceId));
    }
}
