package com.bookly.service;

import com.bookly.entity.AuditEventType;
import com.bookly.entity.AuditLog;
import com.bookly.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Async audit logging service.
 * Writes security events to the audit_logs table without blocking the request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(AuditEventType eventType, UUID actorId, UUID targetId,
                    UUID businessId, String ipAddress, Map<String, Object> details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(eventType)
                    .actorId(actorId)
                    .targetId(targetId)
                    .businessId(businessId)
                    .ipAddress(ipAddress)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit failures must never crash the main request flow
            log.error("Failed to write audit log for event {}: {}", eventType, e.getMessage());
        }
    }
}
