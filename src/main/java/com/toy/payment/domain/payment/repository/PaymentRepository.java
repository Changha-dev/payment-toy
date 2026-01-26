package com.toy.payment.domain.payment.repository;

import com.toy.payment.domain.order.entity.Order;
import com.toy.payment.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrder(Order order);
}
