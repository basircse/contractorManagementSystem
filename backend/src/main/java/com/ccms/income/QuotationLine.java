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
@Table(name = "quotation_line")
public class QuotationLine extends AmountLine {

    @Column(nullable = false)
    private Long quotationId;
}
