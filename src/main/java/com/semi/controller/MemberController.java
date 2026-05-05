package com.semi.controller;

import com.semi.domain.cart.CartItem;
import com.semi.domain.cart.CartItemService;
import com.semi.domain.order.PurchaseOrderService;
import com.semi.security.MemberDetails;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final CartItemService cartItemService;
    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    public String dashboard(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @RequestParam("memberId") String memberId,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        String loginMemberId = memberDetails.getMember().getMemberId();
        if (!loginMemberId.equals(memberId)) {
            return "redirect:/login.html";
        }

        return buildDashboard(memberDetails, model);
    }

    @GetMapping
    public String getDashboard(
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        if (memberDetails == null) {
            return "redirect:/login.html";
        }

        return buildDashboard(memberDetails, model);
    }

    private String buildDashboard(MemberDetails memberDetails, Model model) {
        Long memberId = memberDetails.getMember().getId();
        List<CartItem> recentCartItems = cartItemService.getRecentCartItems(memberId);
        model.addAttribute("dashboardMessage", "대시보드");
        model.addAttribute("member", memberDetails.getMember());
        model.addAttribute("recentCartItems", recentCartItems);
        model.addAttribute("totalOrderCount", purchaseOrderService.getTotalOrderCount(memberId));
        double orderCancellationRate = purchaseOrderService.getOrderCancellationRate(memberId);
        model.addAttribute("orderCancellationRate", orderCancellationRate);
        model.addAttribute("orderCancellationRateText", String.format(Locale.KOREA, "%.1f%%", orderCancellationRate));
        model.addAttribute("totalOrderedProductCount", purchaseOrderService.getTotalOrderedProductCount(memberId));
        double repeatPurchaseRate = purchaseOrderService.getRepeatPurchaseRate(memberId);
        model.addAttribute("repeatPurchaseRate", repeatPurchaseRate);
        model.addAttribute("repeatPurchaseRateText", String.format(Locale.KOREA, "%.1f%%", repeatPurchaseRate));

        return "dashboard";
    }
}
