package com.semi.domain.order.dto;

import com.semi.domain.order.PurchaseOrder;
import java.time.format.DateTimeFormatter;

public record PurchaseOrderResponse(
        Long id,
        String orderNumber,
        String orderDate,
        Integer totalPrice,
        Integer shippingFee,
        Integer finalTotal,
        String shippingAddress,
        String paymentMethod,
        String paymentStatus
) {
    public static PurchaseOrderResponse from(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getOrderedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")),
                order.getTotalPrice(),
                order.getShippingFee(),
                order.getTotalPrice() + order.getShippingFee(),
                order.getShippingAddress(),
                order.getPaymentMethod(),
                order.getPaymentStatus()
        );
    }
}
