package com.ccms.platform;

import com.ccms.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A contractor company = one tenant. */
@Getter
@Setter
@Entity
@Table(name = "contractor")
public class Contractor extends BaseEntity {

    @Column(nullable = false)
    private String name;
    private String ownerName;
    private String phone;
    private String email;
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractorStatus status = ContractorStatus.ACTIVE;

    /** SaaS subscription plan; billing/limits come in the SaaS phase. */
    @Column(nullable = false)
    private String plan = "STANDARD";

    private String blockedReason;
}
