package com.toy.payment.app.payment.controller;

import com.toy.payment.app.common.idempotency.IdempotencyService;
import com.toy.payment.app.payment.dto.PaymentVerifyRequest;
import com.toy.payment.app.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/api/payment/verify")
    public ResponseEntity<String> verifyPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody PaymentVerifyRequest request) {

        // 1. Idempotency Key가 없으면 일반 처리 (하위 호환성)
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("No Idempotency-Key provided. Processing without idempotency check.");
            paymentService.verifyPayment(request.getImp_uid(), request.getMerchant_uid());
            return ResponseEntity.ok("Payment Verified Successfully");
        }

        // 2. 멱등키 형식 검증 (토스: 최대 300자)
        if (idempotencyKey.length() > 300) {
            log.warn("Invalid idempotency key length: {}", idempotencyKey.length());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("INVALID_IDEMPOTENCY_KEY");
        }

        // 3. 캐시된 결과가 있으면 즉시 반환 (COMPLETED 상태)
        Optional<String> cachedResult = idempotencyService.getCachedResult(idempotencyKey);
        if (cachedResult.isPresent()) {
            log.info("Returning cached result for idempotency key: {}", idempotencyKey);
            return ResponseEntity.ok(cachedResult.get());
        }

        // 4. 락 획득 시도
        if (idempotencyService.tryAcquireLock(idempotencyKey)) {
            // 락 획득 성공 → 비즈니스 로직 실행
            return processPaymentWithLock(idempotencyKey, request);
        } else {
            // 락 획득 실패 → 409 Conflict 반환 (PROCESSING 상태, 토스 방식!)
            log.info("Lock acquisition failed. Returning 409 Conflict: {}", idempotencyKey);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("IDEMPOTENT_REQUEST_PROCESSING");
        }
    }

    /**
     * 락 획득 후 결제 검증 수행
     */
    private ResponseEntity<String> processPaymentWithLock(
            String idempotencyKey, PaymentVerifyRequest request) {
        try {
            // 5. 한번 더 캐시 확인 (락 대기 중 다른 요청이 완료했을 수 있음)
            Optional<String> cachedResult = idempotencyService.getCachedResult(idempotencyKey);
            if (cachedResult.isPresent()) {
                idempotencyService.releaseLock(idempotencyKey);
                return ResponseEntity.ok(cachedResult.get());
            }

            // 6. 결제 검증 비즈니스 로직 실행
            // TODO: 테스트 완료 후 삭제 - 동시 요청 테스트용 5초 지연
            try {
                log.info("[TEST] 5초 지연 시작 - idempotencyKey: {}", idempotencyKey);
                Thread.sleep(5000);
                log.info("[TEST] 5초 지연 완료 - idempotencyKey: {}", idempotencyKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // TODO: 테스트 완료 후 삭제 - Mock 모드 (imp_uid가 mock_으로 시작하면 검증 스킵)
            if (request.getImp_uid().startsWith("mock_")) {
                log.info("[MOCK] 결제 검증 스킵 - idempotencyKey: {}", idempotencyKey);
            } else {
                paymentService.verifyPayment(request.getImp_uid(), request.getMerchant_uid());
            }
            String result = "Payment Verified Successfully";

            // 7. 결과 캐싱 (24시간 TTL)
            idempotencyService.cacheResult(idempotencyKey, result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Payment verification failed for idempotency key: {}", idempotencyKey, e);
            throw e;
        } finally {
            // 8. 락 해제 (성공/실패 모두)
            idempotencyService.releaseLock(idempotencyKey);
        }
    }
}
