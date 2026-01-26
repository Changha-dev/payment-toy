package com.toy.payment.domain.order.repository;

import com.toy.payment.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o join fetch o.member join fetch o.product where o.orderUid = :orderUid")
    Optional<Order> findByOrderUidFetch(String orderUid);
}
