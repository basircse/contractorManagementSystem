package com.ccms.costing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WageCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void fullDayWithAllowance() {
        assertThat(WageCalculator.cost(bd("1"), bd("0"), bd("800"), bd("50"), bd("100")))
                .isEqualByComparingTo("850.00");
    }

    @Test
    void halfDayPlusManualOvertime() {
        // 0.5 x (800 + 50) + 2h x 100
        assertThat(WageCalculator.cost(bd("0.5"), bd("2"), bd("800"), bd("50"), bd("100")))
                .isEqualByComparingTo("625.00");
    }

    @Test
    void roundsToPaisa() {
        assertThat(WageCalculator.cost(bd("0.33"), bd("0"), bd("700"), bd("0"), bd("0")))
                .isEqualByComparingTo("231.00");
        assertThat(WageCalculator.cost(bd("0.25"), bd("0.5"), bd("655"), bd("0"), bd("87.33")))
                .isEqualByComparingTo("207.42");
    }
}
