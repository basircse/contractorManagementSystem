package com.ccms.audit;

import com.ccms.security.CurrentUser;
import com.ccms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    public void log(String action, String entityType, Long entityId, String details) {
        logFor(TenantContext.get(), action, entityType, entityId, details);
    }

    public void logFor(Long contractorId, String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setContractorId(contractorId);
        entry.setUserId(CurrentUser.userIdOrNull());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        repo.save(entry);
    }
}
