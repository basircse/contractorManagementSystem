package com.ccms.common;

import com.ccms.tenant.TenantContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Belt-and-braces check for load-by-id: Hibernate's tenant filter covers queries,
 * this makes sure an id from another tenant is treated as "not found".
 */
public final class TenantGuard {

    private TenantGuard() {
    }

    public static <T extends TenantEntity> T own(Optional<T> found, String what, Object id) {
        return found.filter(e -> Objects.equals(e.getContractorId(), TenantContext.get()))
                .orElseThrow(() -> ApiException.notFound(what, id));
    }
}
