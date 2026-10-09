package com.ccms.costing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One line of the cost ledger. Rows are never updated or deleted: a correction is a
 * new row with the negated amount and {@code reversalOf} pointing at the original.
 * Every cost report is a SUM over this table.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "cost_entry")
public class CostEntry {

    public enum SourceType { LABOUR, VENDOR, MATERIAL, OVERHEAD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "contractor_id", nullable = false, updatable = false)
    private Long contractorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;
    private Long sourceId;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private Long siteId;
    private Long buildingId;
    private Long floorId;
    private Long unitId;
    private Long workItemId;
    private Long labourId;

    @Column(nullable = false)
    private BigDecimal days = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal otHours = BigDecimal.ZERO;
    private BigDecimal dailyRate;
    private BigDecimal otHourlyRate;
    private BigDecimal skillAllowance;

    @Column(nullable = false)
    private BigDecimal amount;

    private Long reversalOf;
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** A row that cancels this one. */
    public CostEntry reversal(Long userId) {
        CostEntry r = new CostEntry();
        r.sourceType = sourceType;
        r.sourceId = sourceId;
        r.entryDate = entryDate;
        r.siteId = siteId;
        r.buildingId = buildingId;
        r.floorId = floorId;
        r.unitId = unitId;
        r.workItemId = workItemId;
        r.labourId = labourId;
        r.days = days.negate();
        r.otHours = otHours.negate();
        r.dailyRate = dailyRate;
        r.otHourlyRate = otHourlyRate;
        r.skillAllowance = skillAllowance;
        r.amount = amount.negate();
        r.reversalOf = id;
        r.createdBy = userId;
        return r;
    }
}
