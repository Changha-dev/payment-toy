package com.toy.payment.domain.order.entity;

import com.toy.payment.domain.member.entity.Member;
import com.toy.payment.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private Long count;

    private Long price; // 주문 당시 가격

    private String orderUid; // PG사 주문 번호 (UUID)

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(Member member, Product product, Long count) {
        this.member = member;
        this.product = product;
        this.count = count;
        this.price = product.getPrice() * count;
        this.orderUid = UUID.randomUUID().toString();
        this.status = OrderStatus.PENDING;
    }

    public void completePayment() {
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}
