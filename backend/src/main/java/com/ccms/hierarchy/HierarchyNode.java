package com.ccms.hierarchy;

import com.ccms.common.RecordStatus;
import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Shared columns of Client, Site, Building, Floor and Unit. */
@Getter
@Setter
@MappedSuperclass
public abstract class HierarchyNode extends TenantEntity {

    @Column(nullable = false)
    private String name;
    private String code;
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;
}
