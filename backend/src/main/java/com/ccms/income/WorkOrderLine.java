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
@Table(name = "work_order_line")
public class WorkOrderLine extends AmountLine {

    @Column(nullable = false)
    private Long workOrderId;
}
