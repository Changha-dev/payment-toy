package com.toy.payment.app.payment.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class PortOneService {

    private final RestTemplate restTemplate;

    @Value("${portone.api.key}")
    private String apiKey;

    @Value("${portone.api.secret}")
    private String apiSecret;

    // 타임아웃 설정 (NicePay 가이드 참고)
    private static final int CONNECTION_TIMEOUT_MS = 5000; // 5초
    private static final int READ_TIMEOUT_MS = 30000; // 30초

    public PortOneService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 결제 정보 조회 (타임아웃 시 망취소 처리)
     */
    public PortOnePaymentResponse getPaymentInfo(String impUid, String merchantUid) {
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            return fetchPaymentInfo(impUid, merchantUid, entity);
        } catch (ResourceAccessException e) {
            // Read-timeout 발생 → 망취소 처리
            log.error("Read-timeout 발생! 망취소 진행 - impUid: {}", impUid, e);
            handleNetworkTimeout(impUid, merchantUid);
            throw new PaymentTimeoutException("결제 처리 중 타임아웃이 발생했습니다. 결제가 취소되었습니다.", e);
        }
    }

    private PortOnePaymentResponse fetchPaymentInfo(String impUid, String merchantUid, HttpEntity<Void> entity) {
        try {
            String url = "https://api.iamport.kr/payments/" + impUid;
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return parseResponse(response.getBody());
        } catch (ResourceAccessException e) {
            // 타임아웃 예외는 상위로 전파
            throw e;
        } catch (Exception e) {
            log.warn("imp_uid({})로 조회 실패. merchant_uid({})로 재시도...", impUid, merchantUid);

            try {
                String url = "https://api.iamport.kr/payments/find/" + merchantUid;
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                return parseResponse(response.getBody());
            } catch (Exception ex) {
                log.error("결제 조회 실패 - impUid: {}, merchantUid: {}", impUid, merchantUid, ex);
                throw new RuntimeException("결제 정보 조회에 실패했습니다.");
            }
        }
    }

    /**
     * 타임아웃 발생 시 망취소 처리
     * NicePay 가이드: Read-timeout 발생 시 반드시 망취소 요청
     */
    private void handleNetworkTimeout(String impUid, String merchantUid) {
        log.info("망취소(Network Cancel) 시작 - impUid: {}, merchantUid: {}", impUid, merchantUid);

        boolean cancelSuccess = cancelPayment(impUid, "Read-timeout 발생으로 인한 망취소");

        if (cancelSuccess) {
            log.info("망취소 성공 - impUid: {}", impUid);
        } else {
            // 망취소도 실패한 경우 → 수동 처리 필요
            log.error("망취소 실패! 수동 처리 필요 - impUid: {}, merchantUid: {}", impUid, merchantUid);
            // TODO: 알림 발송 (Slack, Email 등)
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
        private String status;
    }

    /**
     * 결제 타임아웃 예외
     */
    public static class PaymentTimeoutException extends RuntimeException {
        public PaymentTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
