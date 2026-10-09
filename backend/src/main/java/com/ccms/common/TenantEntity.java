package com.ccms.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base for all contractor-owned data. Hibernate fills {@code contractorId} on insert and
 * adds {@code contractor_id = :tenant} to every HQL/criteria query, so one contractor can
 * never read another's rows. Native SQL is NOT filtered - always pass the tenant explicitly.
 */
@Getter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @TenantId
    @Column(name = "contractor_id", nullable = false, updatable = false)
    private Long contractorId;
}
