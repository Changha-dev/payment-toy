package com.toy.payment.app.payment.service;

import com.toy.payment.app.payment.service.PortOneService.PortOnePaymentResponse;
import com.toy.payment.domain.order.entity.Order;
import com.toy.payment.domain.order.repository.OrderRepository;
import com.toy.payment.domain.payment.entity.Payment;
import com.toy.payment.domain.payment.entity.PaymentStatus;
import com.toy.payment.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PortOneService portOneService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void verifyPayment_Success_And_Idempotency() {
        // Given
        String impUid = "imp_12345";
        String merchantUid = "order_uuid_123";
        Long amount = 10000L;

        Order order = mock(Order.class);
        when(order.getPrice()).thenReturn(amount);
        when(order.getId()).thenReturn(1L);

        PortOnePaymentResponse response = new PortOnePaymentResponse();
        response.setStatus("paid");
        response.setAmount(amount);
        response.setMerchantUid(merchantUid);
        response.setImpUid(impUid);

        when(portOneService.getPaymentInfo(eq(impUid), eq(merchantUid))).thenReturn(response);
        when(orderRepository.findByOrderUidFetch(merchantUid)).thenReturn(Optional.of(order));

        // Case 1: First call (New Payment)
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.empty());

        // When
        paymentService.verifyPayment(impUid, merchantUid);

        // Then
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(order, times(1)).completePayment();

        // Case 2: Second call (Duplicate / Webhook)
        Payment existingPayment = mock(Payment.class);
        when(existingPayment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(existingPayment));

        // When
        paymentService.verifyPayment(impUid, merchantUid);

        // Then
        // Should NOT save again or complete payment again
        verify(paymentRepository, times(1)).save(any(Payment.class)); // Count remains 1 from first call
        verify(order, times(1)).completePayment(); // Count remains 1
    }
}
