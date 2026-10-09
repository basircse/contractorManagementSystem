package com.ccms.income;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Invoice / running bill to the client. Net = gross - retention - deductions (VAT, AIT ...).
 * Only SUBMITTED bills count as receivable.
 */
@Getter
@Setter
@Entity
@Table(name = "client_bill")
public class ClientBill extends TenantEntity {

    public enum Status { DRAFT, SUBMITTED, CANCELLED }

    @Column(nullable = false)
    private String billNo;
    @Column(nullable = false)
    private LocalDate billDate;
    private LocalDate dueDate;
    @Column(nullable = false)
    private Long workOrderId;
    @Column(nullable = false)
    private Long clientId;
    @Column(nullable = false)
    private Long siteId;
    private Long milestoneId;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private BigDecimal grossAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal retentionAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal deductionAmount = BigDecimal.ZERO;
    private String deductionNote;
    @Column(nullable = false)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    private String notes;
}
