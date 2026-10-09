package com.ccms.expense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Work handed to a third party (electrical, tiles fitting, piling ...) at an agreed rate or lump sum. */
@Getter
@Setter
@Entity
@Table(name = "subcontract")
public class Subcontract extends LocatedEntity {

    public enum Status { ACTIVE, COMPLETED, CANCELLED }

    @Column(nullable = false)
    private Long partyId;
    @Column(nullable = false)
    private String title;
    private String uom;
    private BigDecimal quantity;
    private BigDecimal rate;
    @Column(nullable = false)
    private BigDecimal contractAmount;
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;
    private String note;
}
