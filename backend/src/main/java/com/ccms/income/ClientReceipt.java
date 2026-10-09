package com.ccms.income;

import com.ccms.common.PayMethod;
import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money collected from a client. Without a bill it is an advance on the work order. */
@Getter
@Setter
@Entity
@Table(name = "client_receipt")
public class ClientReceipt extends TenantEntity {

    @Column(nullable = false)
    private LocalDate receiptDate;
    @Column(nullable = false)
    private Long clientId;
    @Column(nullable = false)
    private Long workOrderId;
    private Long billId;
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayMethod method = PayMethod.CASH;
    private String reference;
    private String note;
}
