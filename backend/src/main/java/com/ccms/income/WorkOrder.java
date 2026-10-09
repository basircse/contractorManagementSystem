package com.ccms.income;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The agreed job for a client at one site; the basis for billing. */
@Getter
@Setter
@Entity
@Table(name = "work_order")
public class WorkOrder extends TenantEntity {

    public enum Status { ACTIVE, COMPLETED, CANCELLED }

    @Column(nullable = false)
    private String woNo;
    @Column(nullable = false)
    private LocalDate woDate;
    @Column(nullable = false)
    private Long clientId;
    @Column(nullable = false)
    private Long siteId;
    private Long quotationId;
    @Column(nullable = false)
    private String title;
    private String clientRef;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false)
    private BigDecimal retentionPercent = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal contractValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    private String notes;
}
