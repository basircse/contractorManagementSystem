package com.ccms.labour;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Money handed to a worker. Advances and wage payments both reduce the amount due. */
@Getter
@Setter
@Entity
@Table(name = "labour_payment")
public class LabourPayment extends TenantEntity {

    public enum Type { WAGE, ADVANCE }

    @Column(nullable = false)
    private Long labourId;
    @Column(nullable = false)
    private LocalDate payDate;
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String note;
}
