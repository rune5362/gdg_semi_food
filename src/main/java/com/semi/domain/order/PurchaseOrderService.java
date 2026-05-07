package com.semi.domain.order;

import com.semi.domain.cart.CartItemRepository;
import com.semi.domain.mail.MailService;
import com.semi.domain.member.Member;
import com.semi.domain.member.MemberRepository;
import com.semi.domain.member.MemberRole;
import com.semi.domain.order.dto.CreatePurchaseOrderRequest;
import com.semi.domain.product.Product;
import com.semi.domain.product.ProductRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final CartItemRepository cartItemRepository;
    private final MailService mailService;

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
                // PG 미연동 — 결제는 버튼 클릭 = 완료로 시뮬레이션. 클라이언트 입력 무시하고 서버에서 단정.
                .paymentStatus("COMPLETED")
                .build();

        lines.forEach(line -> order.addItem(PurchaseOrderItem.builder()
                .purchaseOrder(order)
                .product(line.product())
                .productName(line.product().getName())
                .price(line.product().getPrice())
                .quantity(line.quantity())
                .build()));

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);

        // payment_status가 COMPLETED이면 ADMIN에게 메일 발송 + 주문된 상품을 장바구니에서 삭제
        if ("COMPLETED".equals(savedOrder.getPaymentStatus())) {
            List<Long> orderedProductIds = lines.stream()
                    .map(line -> line.product().getId())
                    .toList();
            if (!orderedProductIds.isEmpty()) {
                cartItemRepository.deleteByMemberIdAndProductIdIn(memberId, orderedProductIds);
            }
            sendOrderCompletedMail(savedOrder);
        }

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

    private void sendOrderCompletedMail(PurchaseOrder order) {
        try {
            List<Member> admins = memberRepository.findByRole(MemberRole.ADMIN);
            log.info("ADMIN 메일 발송 시작: orderNumber={}, adminCount={}", order.getOrderNumber(), admins.size());
            if (admins.isEmpty()) {
                log.warn("ADMIN 회원이 없어 메일 발송을 건너뜁니다.");
                return;
            }

            String subject = "[DaDream] 새로운 구매주문이 접수되었습니다 - " + order.getOrderNumber();
            String text = String.format(
                "새로운 구매주문이 접수되었습니다.\n\n" +
                "주문번호: %s\n" +
                "주문자: %s\n" +
                "결제금액: %,d원\n" +
                "결제상태: %s\n" +
                "주문일시: %s\n",
                order.getOrderNumber(),
                order.getMember().getName(),
                order.getTotalPrice(),
                order.getPaymentStatus(),
                order.getOrderedAt()
            );

            for (Member admin : admins) {
                log.info("ADMIN 메일 발송 대상: email={}", admin.getEmail());
                mailService.sendSimpleMail(admin.getEmail(), subject, text);
            }
            log.info("ADMIN 메일 발송 완료: orderNumber={}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("ADMIN 메일 발송 실패: orderNumber={}", order.getOrderNumber(), e);
        }
    }
}
