package com.toy.payment.app.order.dto;

import lombok.Data;

@Data
public class OrderCreateRequest {
    private Long memberId; // For demo, we pass it. In real app, from SecurityContext.
    private Long productId;
    private Long count;
}
