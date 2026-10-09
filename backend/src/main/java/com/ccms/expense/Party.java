package com.ccms.expense;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A third party the contractor deals with: supplier, sub-contractor, equipment / shuttering owner. */
@Getter
@Setter
@Entity
@Table(name = "party")
public class Party extends TenantEntity {

    public enum Type { SUPPLIER, SUBCONTRACTOR, RENTAL, OTHER }

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String trade;
    private String phone;
    private String address;
    private String note;
    private boolean active = true;
}
