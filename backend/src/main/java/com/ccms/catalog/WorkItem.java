package com.ccms.catalog;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An item / activity of work (Brickwork, Plastering, Column Casting ...). */
@Getter
@Setter
@Entity
@Table(name = "work_item")
public class WorkItem extends TenantEntity {

    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String nameBn;
    @Column(nullable = false)
    private String nameEn;
    /** SFT, CFT, RFT, NOS, LS ... */
    @Column(nullable = false)
    private String uom;
    private int sortOrder;
    private boolean active = true;
}
