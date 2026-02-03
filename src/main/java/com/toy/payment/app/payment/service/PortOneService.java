package com.toy.payment.app.payment.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortOneService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${portone.api.key}")
    private String apiKey; // V1 REST API Key

    @Value("${portone.api.secret}")
    private String apiSecret; // V1 REST API Secret

    public PortOnePaymentResponse getPaymentInfo(String impUid, String merchantUid) {
        // 1. Get Access Token (V1)
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // 2. Request Payment Info (V1) - Try find by imp_uid first
            String url = "https://api.iamport.kr/payments/" + impUid;
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.warn("Failed to find payment by imp_uid ({}). Trying 'find' by merchant_uid ({})...", impUid,
                    merchantUid);

            try {
                // 3. Fallback: Request Payment Info by merchant_uid
                String url = "https://api.iamport.kr/payments/find/" + merchantUid;
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                return parseResponse(response.getBody());
            } catch (Exception ex) {
                log.error(
                        "Payment Lookup Failed via both imp_uid and merchant_uid. This confirms an ACCOUNT MISMATCH between Frontend (imp64266311) and Backend (API Key 7487...).",
                        ex);
                throw new RuntimeException(
                        "Payment verification failed. Please check if your 'Merchant ID' in HTML matches the 'API Key' account in application.yml.");
            }
        }
    }

    private PortOnePaymentResponse parseResponse(Map<String, Object> body) {
        if (body == null || !((Integer) body.get("code")).equals(0)) {
            throw new RuntimeException("Failed to get payment info: " + body);
        }

        Map<String, Object> responseData = (Map<String, Object>) body.get("response");

        PortOnePaymentResponse paymentResponse = new PortOnePaymentResponse();
        paymentResponse.setImpUid((String) responseData.get("imp_uid"));
        paymentResponse.setMerchantUid((String) responseData.get("merchant_uid"));
        paymentResponse.setAmount(((Number) responseData.get("amount")).longValue());
        paymentResponse.setStatus((String) responseData.get("status"));

        return paymentResponse;
    }

    private String getAccessToken() {
        String url = "https://api.iamport.kr/users/getToken";

        Map<String, String> body = new HashMap<>();
        body.put("imp_key", apiKey);
        body.put("imp_secret", apiSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null || !((Integer) responseBody.get("code")).equals(0)) {
                throw new RuntimeException("Failed to get access token: " + responseBody);
            }

            Map<String, Object> responseData = (Map<String, Object>) responseBody.get("response");
            return (String) responseData.get("access_token");
        } catch (Exception e) {
            log.error("Failed to get V1 Access Token", e);
            throw new RuntimeException("Failed to authenticate with PortOne V1: " + e.getMessage());
        }
    }

    /**
     * 결제 취소 (V1 API)
     * 
     * @param impUid PortOne 결제 고유 ID
     * @param reason 취소 사유
     * @return 취소 성공 여부
     */
    public boolean cancelPayment(String impUid, String reason) {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("imp_uid", impUid);
        body.put("reason", reason);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            String url = "https://api.iamport.kr/payments/cancel";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && ((Integer) responseBody.get("code")).equals(0)) {
                log.info("결제 취소 성공 - impUid: {}, reason: {}", impUid, reason);
                return true;
            } else {
                log.error("결제 취소 실패 - impUid: {}, response: {}", impUid, responseBody);
                return false;
            }
        } catch (Exception e) {
            log.error("결제 취소 API 호출 실패 - impUid: {}", impUid, e);
            return false;
        }
    }

    @Data
    public static class PortOnePaymentResponse {
        private String impUid;
        private String merchantUid;
        private Long amount;
        private String status; // "paid", "ready", "failed", "cancelled"
    }
}
