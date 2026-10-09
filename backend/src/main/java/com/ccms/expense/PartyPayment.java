package com.ccms.expense;

import com.ccms.common.PayMethod;
import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money paid to a third party. More than charged = an advance. */
@Getter
@Setter
@Entity
@Table(name = "party_payment")
public class PartyPayment extends TenantEntity {

    @Column(nullable = false)
    private Long partyId;
    @Column(nullable = false)
    private LocalDate payDate;
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayMethod method = PayMethod.CASH;
    private String reference;
    /** Set when the payment was made on the spot with a purchase. */
    private Long purchaseId;
    private String note;
}
