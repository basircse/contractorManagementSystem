package com.ccms.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A priced line of a quotation, work order or bill: quantity x rate = amount. */
@Getter
@Setter
@MappedSuperclass
public abstract class AmountLine extends TenantEntity {

    @Column(nullable = false)
    private int lineNo;
    private Long workItemId;
    @Column(nullable = false)
    private String description;
    private String uom;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(nullable = false)
    private BigDecimal rate;
    @Column(nullable = false)
    private BigDecimal amount;
}
