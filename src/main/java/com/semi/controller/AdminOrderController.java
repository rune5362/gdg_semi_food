package com.semi.controller;

import com.semi.controller.dto.AdminOrderPageResponse;
import com.semi.controller.dto.FilteredExportRequest;
import com.semi.domain.order.PurchaseOrder;
import com.semi.domain.order.PurchaseOrderRepository;
import com.semi.domain.order.OrderStatus;
import com.semi.domain.order.dto.OrderResponse;
import com.semi.domain.order.dto.OrderItemResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> orders = purchaseOrderRepository.findAll(
                        Sort.by(Sort.Direction.ASC, "orderedAt").and(Sort.by(Sort.Direction.ASC, "orderNumber"))
                ).stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/list/paged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminOrderPageResponse> getOrdersPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "orderedAt").and(Sort.by(Sort.Direction.ASC, "orderNumber"))
        );
        var orderPage = purchaseOrderRepository.findAll(pageable);
        List<OrderResponse> orders = orderPage.stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        return ResponseEntity.ok(new AdminOrderPageResponse(
                orders,
                purchaseOrderRepository.countByStatus(OrderStatus.RECEIVED)
                        + purchaseOrderRepository.countByStatus(OrderStatus.IN_PROGRESS),
                purchaseOrderRepository.countByOrderedAtBetween(startOfDay, endOfDay),
                orderPage.hasNext()
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        OrderResponse response = convertToOrderResponse(order);
        return ResponseEntity.ok(response);
    }

    private OrderResponse convertToOrderResponse(PurchaseOrder order) {
        // Convert order items
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> {                  
                    OrderItemResponse response = new OrderItemResponse(
                            item.getId(),
                            item.getProductName(),
                            item.getDescription(),
                            item.getQuantity(),
                            item.getUnitPrice(),  // Use getUnitPrice() method
                            item.getTotalPrice()   // Use getTotalPrice() method
                    );
                    return response;
                })
                .collect(Collectors.toList());

        // Convert status
        OrderResponse.OrderStatus status = convertStatus(order.getStatus());

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getMember().getName(),
                order.getMember().getEmail(),
                order.getMember().getPhone(),
                defaultText(order.getShippingAddress(), "주소 정보 없음"),
                defaultText(order.getPaymentMethod(), "결제 수단 정보 없음"),
                defaultText(order.getPaymentStatus(), "결제 상태 정보 없음"),
                status,
                order.getOrderedAt(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getTotalPrice(),
                items
        );
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportOrdersToExcel() {
        try {
            List<PurchaseOrder> orders = purchaseOrderRepository.findAll();
            
            // Create Excel workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Orders");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Order Number", "Customer Name", "Email", "Phone", "Address", "Payment Method", "Payment Status", "Order Status", "Order Date", "Subtotal", "Shipping Fee", "Total Price"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // Fill data rows
            int rowNum = 1;
            for (PurchaseOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(order.getOrderNumber());
                row.createCell(1).setCellValue(order.getMember().getName());
                row.createCell(2).setCellValue(order.getMember().getEmail());
                row.createCell(3).setCellValue(order.getMember().getPhone());
                row.createCell(4).setCellValue(order.getShippingAddress());
                row.createCell(5).setCellValue(order.getPaymentMethod());
                row.createCell(6).setCellValue(order.getPaymentStatus());
                row.createCell(7).setCellValue(order.getStatus().toString());
                row.createCell(8).setCellValue(order.getOrderedAt().toString());
                row.createCell(9).setCellValue(order.getSubtotal());
                row.createCell(10).setCellValue(order.getShippingFee());
                row.createCell(11).setCellValue(order.getTotalPrice());
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            
            byte[] excelBytes = outputStream.toByteArray();
            
            // Create filename with current date
            String filename = "orders_export_" + LocalDate.now().toString() + ".xlsx";
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelBytes);
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/export/filtered")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportFilteredOrdersToExcel(@RequestBody FilteredExportRequest request) {
        try {
            // Get orders by IDs
            List<PurchaseOrder> orders = purchaseOrderRepository.findAllById(request.getOrderIds());
            
            // Create Excel workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Filtered Orders");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Order Number", "Customer Name", "Email", "Phone", "Address", "Payment Method", "Payment Status", "Order Status", "Order Date", "Subtotal", "Shipping Fee", "Total Price"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            
            // Fill data rows
            int rowNum = 1;
            for (PurchaseOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                
                row.createCell(0).setCellValue(order.getOrderNumber());
                row.createCell(1).setCellValue(order.getMember().getName());
                row.createCell(2).setCellValue(order.getMember().getEmail());
                row.createCell(3).setCellValue(order.getMember().getPhone());
                row.createCell(4).setCellValue(order.getShippingAddress());
                row.createCell(5).setCellValue(order.getPaymentMethod());
                row.createCell(6).setCellValue(order.getPaymentStatus());
                row.createCell(7).setCellValue(order.getStatus().toString());
                row.createCell(8).setCellValue(order.getOrderedAt().toString());
                row.createCell(9).setCellValue(order.getSubtotal());
                row.createCell(10).setCellValue(order.getShippingFee());
                row.createCell(11).setCellValue(order.getTotalPrice());
            }
            
            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            
            byte[] excelBytes = outputStream.toByteArray();
            
            // Create filename with search term and current date
            String filename = "orders_filtered_" + request.getSearchTerm() + "_" + LocalDate.now().toString() + ".xlsx";
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(excelBytes);
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private OrderResponse.OrderStatus convertStatus(OrderStatus status) {
        try {
            return OrderResponse.OrderStatus.valueOf(status.name());
        } catch (IllegalArgumentException e) {
            return OrderResponse.OrderStatus.PROCESSING; // Default status
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
