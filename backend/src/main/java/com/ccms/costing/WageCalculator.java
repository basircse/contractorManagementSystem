package com.ccms.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FR-2.4: labour cost of one allocation =
 *   dayFraction x (dailyRate + skillAllowance) + otHours x otHourlyRate.
 * Overtime is entered manually against the allocation it belongs to.
 */
public final class WageCalculator {

    private WageCalculator() {
    }

    public static BigDecimal cost(BigDecimal dayFraction, BigDecimal otHours,
                                  BigDecimal dailyRate, BigDecimal skillAllowance, BigDecimal otHourlyRate) {
        BigDecimal base = dayFraction.multiply(dailyRate.add(skillAllowance));
        BigDecimal ot = otHours.multiply(otHourlyRate);
        return base.add(ot).setScale(2, RoundingMode.HALF_UP);
    }
}
