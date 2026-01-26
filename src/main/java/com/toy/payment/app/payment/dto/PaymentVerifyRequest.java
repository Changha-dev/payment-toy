package com.toy.payment.app.payment.dto;

import lombok.Data;

@Data
public class PaymentVerifyRequest {
    private String imp_uid;
    private String merchant_uid;
}
