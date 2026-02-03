package com.toy.payment.app.payment.service;

import com.toy.payment.app.payment.service.PortOneService.PortOnePaymentResponse;
import com.toy.payment.domain.order.entity.Order;
import com.toy.payment.domain.order.repository.OrderRepository;
import com.toy.payment.domain.payment.entity.Payment;
import com.toy.payment.domain.payment.entity.PaymentStatus;
import com.toy.payment.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PortOneService portOneService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public void verifyPayment(String impUid, String merchantUid) {
        // 1. Payment Verification from PortOne (V1)
        PortOnePaymentResponse paymentResponse = portOneService.getPaymentInfo(impUid, merchantUid);

        // 2. Order Lookup
        Order order = orderRepository.findByOrderUidFetch(merchantUid)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + merchantUid));

        // 3. Amount Verification
        if (order.getPrice().longValue() != paymentResponse.getAmount().longValue()) {
            throw new IllegalArgumentException(
                    "Amount mismatch. Order: " + order.getPrice() + ", Paid: " + paymentResponse.getAmount());
        }

        // 4. Status Check & Update (Idempotency)
        // V1 Status is "paid" (lowercase) or "PAID" (uppercase), let's handle
        // case-insensitively
        String status = paymentResponse.getStatus() != null ? paymentResponse.getStatus().toLowerCase() : "";
        if ("paid".equals(status)) {
            Payment payment = paymentRepository.findByOrder(order)
                    .orElseGet(() -> Payment.builder()
                            .order(order)
                            .price(order.getPrice())
                            .build());

            if (payment.getStatus() == PaymentStatus.PAID) {
                log.info("Payment already processed for order: {}", order.getId());
                return;
            }

            // 5. 재고 차감 (결제 검증 성공 후에만 차감 - 낙관적 락으로 동시성 제어)
            try {
                order.getProduct().decreaseStock(order.getCount());
                log.info("Stock decreased for product: {}, count: {}",
                        order.getProduct().getId(), order.getCount());
            } catch (org.springframework.dao.OptimisticLockingFailureException e) {
                // 재고 충돌 발생 → 이미 결제된 금액 자동 환불
                log.warn("재고 충돌 발생! 결제 취소 진행 - impUid: {}, merchantUid: {}", impUid, merchantUid);
                boolean cancelSuccess = portOneService.cancelPayment(impUid, "재고 소진으로 인한 자동 취소");
                if (cancelSuccess) {
                    throw new StockExhaustedException("재고가 소진되어 결제가 자동 취소되었습니다.");
                } else {
                    throw new StockExhaustedException("재고 소진. 결제 취소 처리 중 문제가 발생했습니다. 고객센터에 문의해주세요.");
                }
            }

            payment.changePaymentBySuccess(PaymentStatus.PAID, impUid);
            paymentRepository.save(payment);

            order.completePayment();
        } else {
            throw new IllegalArgumentException("Payment not paid. Status: " + paymentResponse.getStatus());
        }
    }

    /**
     * 재고 소진 예외
     */
    public static class StockExhaustedException extends RuntimeException {
        public StockExhaustedException(String message) {
            super(message);
        }
    }
}
