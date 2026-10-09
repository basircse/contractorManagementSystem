package com.ccms.expense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Items hired or borrowed for a site (steel shutter, props, bamboo, pins, mixer ...).
 * Rent = quantity still out x rate per day x days; it is posted to the ledger when charged
 * or returned, so a long hire can be charged month by month.
 */
@Getter
@Setter
@Entity
@Table(name = "rental")
public class Rental extends LocatedEntity {

    public enum Status { OUT, RETURNED }

    private Long partyId;
    private Long materialId;
    private String description;
    private String uom;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(nullable = false)
    private BigDecimal quantityOut;
    @Column(nullable = false)
    private BigDecimal ratePerDay;
    @Column(nullable = false)
    private LocalDate startDate;
    private LocalDate chargedUntil;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OUT;
    private String note;

    /** First day not yet charged. */
    public LocalDate nextChargeFrom() {
        return chargedUntil == null ? startDate : chargedUntil.plusDays(1);
    }
}
