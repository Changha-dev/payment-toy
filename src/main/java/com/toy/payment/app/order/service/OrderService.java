package com.toy.payment.app.order.service;

import com.toy.payment.domain.member.entity.Member;
import com.toy.payment.domain.member.repository.MemberRepository;
import com.toy.payment.domain.order.entity.Order;
import com.toy.payment.domain.order.repository.OrderRepository;
import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Order createOrder(Long memberId, Long productId, Long count) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        // Pessimistic Write Lock is applied in Repository
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        product.decreaseStock(count); // Stock check & decrease

        Order order = new Order(member, product, count);
        return orderRepository.save(order);
    }
}
