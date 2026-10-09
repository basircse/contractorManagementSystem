package com.ccms.labour;

import com.ccms.common.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Wage rates for a skill tier, optionally specific to one site, valid from a date.
 * The newest card with effectiveFrom &lt;= work date wins; a site card beats the general one.
 */
@Getter
@Setter
@Entity
@Table(name = "rate_card")
public class RateCard extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillTier skillTier;

    /** Null = all sites. */
    private Long siteId;

    @Column(nullable = false)
    private BigDecimal dailyRate;
    @Column(nullable = false)
    private BigDecimal otHourlyRate = BigDecimal.ZERO;
    /** Paid per full day worked. */
    @Column(nullable = false)
    private BigDecimal skillAllowance = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate effectiveFrom;
}
