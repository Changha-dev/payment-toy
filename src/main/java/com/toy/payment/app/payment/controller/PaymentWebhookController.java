package com.toy.payment.app.payment.controller;

import com.toy.payment.app.payment.dto.PaymentWebhookRequest;
import com.toy.payment.app.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/api/payment/webhook")
    public void handleWebhook(@RequestBody PaymentWebhookRequest request) {
        log.info("Webhook received: {}", request);

        String paymentId = request.getPaymentId();
        if (paymentId == null || paymentId.isEmpty()) {
            paymentId = request.getImp_uid();
        }

        paymentService.verifyPayment(paymentId, request.getMerchant_uid());
    }
}
