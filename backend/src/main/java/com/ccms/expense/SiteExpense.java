package com.ccms.expense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Transport, fuel, food, electricity, small tools and other site spends. */
@Getter
@Setter
@Entity
@Table(name = "site_expense")
public class SiteExpense extends LocatedEntity {

    public enum Category { TRANSPORT, FUEL, FOOD, UTILITY, TOOLS, REPAIR, OFFICE, OTHER }

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private Long partyId;
    @Column(nullable = false)
    private BigDecimal amount;
    private String description;
}
