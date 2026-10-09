package com.ccms.income;

import com.ccms.common.AmountLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "client_bill_line")
public class ClientBillLine extends AmountLine {

    @Column(nullable = false)
    private Long billId;
    /** The work-order line being billed (quantity-based running bills). */
    private Long workOrderLineId;
}
