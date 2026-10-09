package com.ccms.income;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Price offer to a client (BOQ). An accepted quotation becomes a work order. */
@Getter
@Setter
@Entity
@Table(name = "quotation")
public class Quotation extends TenantEntity {

    public enum Status { DRAFT, SENT, ACCEPTED, REJECTED }

    @Column(nullable = false)
    private String quoteNo;
    @Column(nullable = false)
    private LocalDate quoteDate;
    private LocalDate validUntil;
    @Column(nullable = false)
    private Long clientId;
    private Long siteId;
    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;
    private String terms;
    private String notes;
}
