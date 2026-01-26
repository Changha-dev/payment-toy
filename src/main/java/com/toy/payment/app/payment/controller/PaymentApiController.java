package com.toy.payment.app.payment.controller;

import com.toy.payment.app.payment.dto.PaymentVerifyRequest;
import com.toy.payment.app.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;

    @PostMapping("/api/payment/verify")
    public ResponseEntity<String> verifyPayment(@RequestBody PaymentVerifyRequest request) {
        paymentService.verifyPayment(request.getImp_uid(), request.getMerchant_uid());
        return ResponseEntity.ok("Payment Verified Successfully");
    }
}
