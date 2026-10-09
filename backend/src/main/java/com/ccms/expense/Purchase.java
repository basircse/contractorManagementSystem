package com.ccms.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Materials bought for a site (cement, rod, wood, cable ...), on credit from a party or cash. */
@Getter
@Setter
@Entity
@Table(name = "purchase")
public class Purchase extends LocatedEntity {

    @Column(nullable = false)
    private LocalDate purchaseDate;
    private Long partyId;
    private String invoiceNo;
    @Column(nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String note;
}
