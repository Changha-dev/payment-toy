package com.toy.payment.app.payment.dto;

import lombok.Data;

@Data
public class PaymentWebhookRequest {
    private String paymentId; // V2 uses paymentId instead of imp_uid
    private String imp_uid; // Keep for V1 legacy
    private String merchant_uid;
    private String status;
}
