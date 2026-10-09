package com.ccms.catalog;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Cement, rod, wood, bamboo, steel shutter, pins ... RENTABLE items are usually hired or borrowed. */
@Getter
@Setter
@Entity
@Table(name = "material")
public class Material extends TenantEntity {

    public enum Kind { CONSUMABLE, RENTABLE }

    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String nameBn;
    @Column(nullable = false)
    private String nameEn;
    @Column(nullable = false)
    private String uom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind = Kind.CONSUMABLE;

    private int sortOrder;
    private boolean active = true;
}
