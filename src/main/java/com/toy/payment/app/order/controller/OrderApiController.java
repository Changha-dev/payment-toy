package com.toy.payment.app.order.controller;

import com.toy.payment.app.order.dto.OrderCreateRequest;
import com.toy.payment.app.order.dto.OrderResponse;
import com.toy.payment.app.order.service.OrderService;
import com.toy.payment.domain.order.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    @PostMapping("/api/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderCreateRequest request) {
        Order order = orderService.createOrder(
                request.getMemberId(),
                request.getProductId(),
                request.getCount());
        return ResponseEntity.ok(new OrderResponse(order));
    }
}
