package com.ccms.expense;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A rent charge or a (partial) return. Each one posts its rent to the ledger. */
@Getter
@Setter
@Entity
@Table(name = "rental_event")
public class RentalEvent extends TenantEntity {

    public enum Type { CHARGE, RETURN }

    @Column(nullable = false)
    private Long rentalId;
    @Column(nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    /** Quantity returned (RETURN only). */
    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;
    private LocalDate chargeFrom;
    private LocalDate chargeTo;
    @Column(nullable = false)
    private BigDecimal chargedQty = BigDecimal.ZERO;
    private int days;
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;
    private String note;
}
