package com.toy.payment.app.order.dto;

import com.toy.payment.domain.order.entity.Order;
import lombok.Data;

@Data
public class OrderResponse {
    private String orderUid;
    private String itemName;
    private String buyerName;
    private String buyerEmail;
    private String buyerAddress;
    private Long paymentPrice;

    public OrderResponse(Order order) {
        this.orderUid = order.getOrderUid();
        this.itemName = order.getProduct().getName();
        this.buyerName = order.getMember().getName();
        this.buyerEmail = order.getMember().getEmail();
        this.buyerAddress = order.getMember().getAddress();
        this.paymentPrice = order.getPrice();
    }
}
