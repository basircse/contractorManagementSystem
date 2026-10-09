package com.ccms.expense;

import com.ccms.common.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "purchase_line")
public class PurchaseLine extends TenantEntity {

    @Column(nullable = false)
    private Long purchaseId;
    private Long materialId;
    private String description;
    private String uom;
    @Column(nullable = false)
    private BigDecimal quantity;
    @Column(nullable = false)
    private BigDecimal rate;
    @Column(nullable = false)
    private BigDecimal amount;
}
