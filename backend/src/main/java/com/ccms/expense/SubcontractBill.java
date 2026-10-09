package com.ccms.expense;

import com.ccms.common.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Work done by the sub-contractor as measured / claimed; each bill is a cost. */
@Getter
@Setter
@Entity
@Table(name = "subcontract_bill")
public class SubcontractBill extends TenantEntity {

    @Column(nullable = false)
    private Long subcontractId;
    @Column(nullable = false)
    private LocalDate billDate;
    private BigDecimal quantity;
    @Column(nullable = false)
    private BigDecimal amount;
    private String note;
}
