package com.ccms.income;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * A priced line as sent by the UI. {@code id} identifies an existing work-order line when
 * editing; {@code workOrderLineId} links a bill line to the work-order line it bills.
 */
public record LineRequest(Long id, Long workItemId, Long workOrderLineId,
                          @NotBlank @Size(max = 500) String description,
                          @Size(max = 20) String uom,
                          @NotNull @PositiveOrZero @Digits(integer = 11, fraction = 3) BigDecimal quantity,
                          @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal rate) {
}
