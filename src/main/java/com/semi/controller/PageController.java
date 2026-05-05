package com.semi.controller;

import com.semi.domain.order.OrderStatus;
import com.semi.domain.order.PurchaseOrder;
import com.semi.domain.order.PurchaseOrderService;
import com.semi.security.MemberDetails;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final PurchaseOrderService purchaseOrderService;
    private static final DateTimeFormatter ORDERED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final DateTimeFormatter ORDERED_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    @GetMapping("/checkout")
    public String checkout(@AuthenticationPrincipal MemberDetails memberDetails, Model model) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        model.addAttribute("member", memberDetails.getMember());
        return "checkout";
    }

    @GetMapping("/order_detail")
    public String orderDetail(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam(name = "orderNumber", required = false) String orderNumber,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        if (orderNumber == null || orderNumber.isBlank()) {
            return "order_detail";
        }

        model.addAttribute("requestedOrderNumber", orderNumber);
        try {
            PurchaseOrder order = purchaseOrderService.getOrderByMemberIdAndOrderNumber(
                    memberDetails.getMember().getId(),
                    orderNumber
            );
            model.addAttribute("order", OrderDetailRow.from(order));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("orderError", "주문번호 " + orderNumber + "에 해당하는 주문 정보를 찾을 수 없습니다.");
        }
        return "order_detail";
    }

    @GetMapping("/order_success")
    public String orderSuccess(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "orderNumber", required = false) String orderNumber,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        model.addAttribute("status", status);
        if (orderNumber == null || orderNumber.isBlank()) {
            if ("fail".equals(status)) {
                model.addAttribute("orderError", "주문 저장에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }
            return "order_success";
        }

        model.addAttribute("requestedOrderNumber", orderNumber);
        try {
            PurchaseOrder order = purchaseOrderService.getOrderByOrderNumber(orderNumber);
            model.addAttribute("order", OrderDetailRow.from(order));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("orderError", "주문번호 " + orderNumber + "에 해당하는 주문 정보를 찾을 수 없습니다.");
        }
        return "order_success";
    }

    @GetMapping("/all_orders")
    public String allOrders(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam(name = "memberId", required = false) Long memberId,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        Long resolvedMemberId = memberDetails.getMember().getId();
        model.addAttribute("memberId", resolvedMemberId);
        try {
            List<OrderRow> orders = purchaseOrderService.getOrdersByMemberId(resolvedMemberId).stream()
                    .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                    .map(OrderRow::from)
                    .toList();
            model.addAttribute("orders", orders);
        } catch (Exception ex) {
            model.addAttribute("orders", List.of());
            model.addAttribute("ordersError", "주문 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        return "all_orders";
    }

    @GetMapping("/cancel_orders")
    public String cancelOrders(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam(name = "memberId", required = false) Long memberId,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        Long resolvedMemberId = memberDetails.getMember().getId();
        model.addAttribute("memberId", resolvedMemberId);
        try {
            List<OrderRow> orders = purchaseOrderService.getOrdersByMemberId(resolvedMemberId).stream()
                    .filter(order -> order.getStatus() == OrderStatus.CANCELLED)
                    .map(OrderRow::from)
                    .toList();
            model.addAttribute("orders", orders);
        } catch (Exception ex) {
            model.addAttribute("orders", List.of());
            model.addAttribute("ordersError", "취소/환불 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        return "cancel_orders";
    }

    @GetMapping("/mypage")
    public String myPage(
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        model.addAttribute("member", memberDetails.getMember());
        return "mypage";
    }

    public record OrderRow(
            String orderNumber,
            String statusLabel,
            String totalPriceText,
            String orderedAtText,
            String orderedDateText,
            boolean cancellable
    ) {
        private static OrderRow from(PurchaseOrder order) {
            OrderStatus status = order.getStatus();
            return new OrderRow(
                    order.getOrderNumber(),
                    statusLabel(status),
                    formatWon(order.getTotalPrice()),
                    formatDateTime(order.getOrderedAt()),
                    formatDate(order.getOrderedAt()),
                    status == OrderStatus.RECEIVED || status == OrderStatus.IN_PROGRESS
            );
        }

        private static String statusLabel(OrderStatus status) {
            if (status == null) {
                return "상태 확인 중";
            }
            return switch (status) {
                case RECEIVED -> "주문 접수";
                case IN_PROGRESS -> "처리 중";
                case SHIPPED -> "배송 중";
                case COMPLETED -> "주문 완료";
                case CANCELLED -> "취소 완료";
            };
        }

        private static String formatWon(Integer amount) {
            int safeAmount = amount == null ? 0 : amount;
            return String.format(Locale.KOREA, "₩%,d", safeAmount);
        }

        private static String formatDateTime(LocalDateTime orderedAt) {
            return orderedAt == null ? "-" : orderedAt.format(ORDERED_AT_FORMATTER);
        }

        private static String formatDate(LocalDateTime orderedAt) {
            return orderedAt == null ? "-" : orderedAt.format(ORDERED_DATE_FORMATTER);
        }
    }

    public record OrderDetailRow(
            String orderNumber,
            String statusLabel,
            Integer totalPrice,
            Integer shippingFee,
            LocalDateTime orderedAt,
            List<OrderItemRow> items,
            String shippingAddress,
            String paymentMethod,
            String paymentStatus,
            String buyerName,
            String buyerPhone,
            String trackingNumber,
            String estimatedArrival
    ) {
        private static OrderDetailRow from(PurchaseOrder order) {
            LocalDateTime orderedAt = order.getOrderedAt();
            LocalDateTime estimatedDate = orderedAt != null ? orderedAt.plusDays(3) : LocalDateTime.now().plusDays(3);
            String days = new String[]{"일", "월", "화", "수", "목", "금", "토"}[estimatedDate.getDayOfWeek().getValue() % 7];
            String estimatedArrival = String.format("%d월 %d일(%s)", estimatedDate.getMonthValue(), estimatedDate.getDayOfMonth(), days);

            return new OrderDetailRow(
                    order.getOrderNumber(),
                    OrderRow.statusLabel(order.getStatus()),
                    order.getTotalPrice(),
                    order.getShippingFee(),
                    order.getOrderedAt(),
                    order.getItems().stream()
                            .map(item -> new OrderItemRow(
                                    item.getProductName(),
                                    item.getPrice(),
                                    item.getQuantity(),
                                    item.subtotal(),
                                    item.getProduct().getImageUrl()
                            ))
                            .toList(),
                    order.getShippingAddress(),
                    order.getPaymentMethod(),
                    order.getPaymentStatus(),
                    order.getMember() != null ? order.getMember().getName() : "김남해",
                    order.getMember() != null ? order.getMember().getPhone() : "010-1234-5678",
                    "TRK" + order.getOrderNumber().replaceAll("[^0-9]", ""),
                    estimatedArrival
            );
        }
    }

    public record OrderItemRow(
            String productName,
            Integer price,
            Integer quantity,
            Integer subtotal,
            String imageUrl
    ) {
    }
}
