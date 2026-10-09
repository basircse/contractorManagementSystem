package com.ccms.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.function.Function;

/** Taka amounts are kept to 2 decimals, rounded half-up. */
public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal times(BigDecimal qty, BigDecimal rate) {
        if (qty == null || rate == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return of(qty.multiply(rate));
    }

    public static <T> BigDecimal sum(Collection<T> rows, Function<T, BigDecimal> f) {
        return of(rows.stream().map(f).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
