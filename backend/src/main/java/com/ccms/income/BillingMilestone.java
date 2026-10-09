package com.ccms.income;

import com.ccms.common.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One planned bill of a work order: a milestone ("Roof casting") or a weekly / monthly instalment. */
@Getter
@Setter
@Entity
@Table(name = "billing_milestone")
public class BillingMilestone extends TenantEntity {

    @Column(nullable = false)
    private Long workOrderId;
    @Column(nullable = false)
    private int seq;
    @Column(nullable = false)
    private String title;
    private LocalDate dueDate;
    @Column(nullable = false)
    private BigDecimal amount;
    /** Set once a bill has been raised for this milestone. */
    private Long billId;
}
