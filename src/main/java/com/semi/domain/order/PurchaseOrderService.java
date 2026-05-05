package com.semi.domain.order;

import com.semi.domain.member.Member;
import com.semi.domain.member.MemberRepository;
import com.semi.domain.order.dto.CreatePurchaseOrderRequest;
import com.semi.domain.product.Product;
import com.semi.domain.product.ProductRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public PurchaseOrder createOrder(Long memberId, CreatePurchaseOrderRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("member not found. id=" + memberId));

        List<OrderLine> lines = request.items().stream()
                .map(item -> {
                    Product product = productRepository.findById(item.productId())
                            .orElseThrow(() -> new IllegalArgumentException("product not found. id=" + item.productId()));
                    return new OrderLine(product, item.quantity());
                })
                .toList();

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order items are required.");
        }

        int totalPrice = lines.stream()
                .mapToInt(line -> line.product().getPrice() * line.quantity())
                .sum();

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(nextOrderNumber())
                .member(member)
                .supplier(lines.get(0).product().getSupplier())
                .totalPrice(totalPrice)
                .shippingFee(0)
                .isAuto(false)
                .subtotal(totalPrice)
                .shippingAddress(request.shippingAddress())
                .paymentMethod(request.paymentMethod())
                .paymentStatus(request.paymentStatus())
                .build();

        lines.forEach(line -> order.addItem(PurchaseOrderItem.builder()
                .purchaseOrder(order)
                .product(line.product())
                .productName(line.product().getName())
                .price(line.product().getPrice())
                .quantity(line.quantity())
                .build()));

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);
        return savedOrder;
    }

    @Transactional
    public PurchaseOrder cancelOrder(Long memberId, String orderNumber) {
        PurchaseOrder order = purchaseOrderRepository.findByMemberIdAndOrderNumber(memberId, orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("order not found. orderNumber=" + orderNumber));

        if (order.getStatus() != OrderStatus.RECEIVED && order.getStatus() != OrderStatus.IN_PROGRESS) {
            throw new IllegalStateException("order cannot be cancelled. orderNumber=" + orderNumber);
        }

        order.updateStatus(OrderStatus.CANCELLED);
        return order;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> getOrdersByMemberId(Long memberId) {
        return purchaseOrderRepository.findByMemberIdOrderByOrderedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderItem> searchOrderedItems(Long memberId, String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            return List.of();
        }

        return purchaseOrderItemRepository.searchMemberOrderItems(memberId, trimmedQuery);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOrderByMemberIdAndOrderNumber(Long memberId, String orderNumber) {
        return purchaseOrderRepository.findDetailByMemberIdAndOrderNumber(memberId, orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("order not found. orderNumber=" + orderNumber));
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOrderByOrderNumber(String orderNumber) {
        return purchaseOrderRepository.findDetailByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("order not found. orderNumber=" + orderNumber));
    }

    @Transactional(readOnly = true)
    public long getTotalOrderCount() {
        return purchaseOrderRepository.count();
    }

    @Transactional(readOnly = true)
    public long getTotalOrderCount(Long memberId) {
        return purchaseOrderRepository.countByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public double getOrderCancellationRate() {
        long totalOrderCount = purchaseOrderRepository.count();
        if (totalOrderCount == 0) {
            return 0.0;
        }

        long cancelledOrderCount = purchaseOrderRepository.countByStatus(OrderStatus.CANCELLED);
        return (cancelledOrderCount * 100.0) / totalOrderCount;
    }

    @Transactional(readOnly = true)
    public double getOrderCancellationRate(Long memberId) {
        long totalOrderCount = purchaseOrderRepository.countByMemberId(memberId);
        if (totalOrderCount == 0) {
            return 0.0;
        }

        long cancelledOrderCount = purchaseOrderRepository.countByMemberIdAndStatus(memberId, OrderStatus.CANCELLED);
        return (cancelledOrderCount * 100.0) / totalOrderCount;
    }

    @Transactional(readOnly = true)
    public long getTotalOrderedProductCount() {
        return purchaseOrderItemRepository.sumOrderedQuantity();
    }

    @Transactional(readOnly = true)
    public long getTotalOrderedProductCount(Long memberId) {
        return purchaseOrderItemRepository.countDistinctOrderedProductsByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public double getRepeatPurchaseRate() {
        long distinctProductCount = purchaseOrderItemRepository.countDistinctOrderedProducts();
        if (distinctProductCount == 0) {
            return 0.0;
        }

        long repeatOrderedProductCount = purchaseOrderItemRepository.countRepeatOrderedProducts();
        return (repeatOrderedProductCount * 100.0) / distinctProductCount;
    }

    @Transactional(readOnly = true)
    public double getRepeatPurchaseRate(Long memberId) {
        long distinctProductCount = purchaseOrderItemRepository.countDistinctOrderedProductsByMemberId(memberId);
        if (distinctProductCount == 0) {
            return 0.0;
        }

        long repeatOrderedProductCount = purchaseOrderItemRepository.countRepeatOrderedProductsByMemberId(memberId);
        return (repeatOrderedProductCount * 100.0) / distinctProductCount;
    }

    private String nextOrderNumber() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        long sequence = purchaseOrderRepository.countByOrderedAtBetween(start, end) + 1;
        return "PO-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }

    private record OrderLine(Product product, int quantity) {
    }
}
