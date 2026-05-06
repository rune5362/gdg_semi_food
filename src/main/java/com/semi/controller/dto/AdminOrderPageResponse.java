package com.semi.controller.dto;

import com.semi.domain.order.dto.OrderResponse;

import java.util.List;

public record AdminOrderPageResponse(
        List<OrderResponse> orders,
        long activeCount,
        long todayCount,
        boolean hasMore
) {
}
