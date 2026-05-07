package com.semi.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreatePurchaseOrderRequest(
        @NotEmpty List<@Valid Item> items,
        @Min(0) int usedPoints,
        String shippingAddress,
        String paymentMethod
) {
    public record Item(
            @NotNull Long productId,
            @Min(1) int quantity
    ) {
    }
}
