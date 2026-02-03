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

/**
 * 결제 검증 API Controller (블로그 방식)
 * 
 * <h2>동작 흐름</h2>
 * 
 * <pre>
 * 1. 락 획득 시도
 * 2. 락 획득 성공 → 캐시 확인 → 비즈니스 로직 → 결과 캐싱 → 락 해제
 * 3. 락 획득 실패 → 대기 → 캐시된 결과 반환
 * </pre>
 * 
 * <h2>장점</h2>
 * <ul>
 * <li>동시 요청 시에도 모든 요청이 성공 응답을 받을 수 있음</li>
 * <li>409 Conflict 대신 대기 후 캐시 결과 반환</li>
 * </ul>
 */
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

        // 2. 캐시된 결과가 있으면 즉시 반환
        Optional<String> cachedResult = idempotencyService.getCachedResult(idempotencyKey);
        if (cachedResult.isPresent()) {
            log.info("Returning cached result for idempotency key: {}", idempotencyKey);
            return ResponseEntity.ok(cachedResult.get());
        }

        // 3. 락 획득 시도
        if (idempotencyService.tryAcquireLock(idempotencyKey)) {
            // 락 획득 성공 → 비즈니스 로직 실행
            return processPaymentWithLock(idempotencyKey, request);
        } else {
            // 락 획득 실패 → 대기 후 캐시 결과 반환 (블로그 방식!)
            return waitAndReturnCachedResult(idempotencyKey);
        }
    }

    /**
     * 락 획득 후 결제 검증 수행
     */
    private ResponseEntity<String> processPaymentWithLock(
            String idempotencyKey, PaymentVerifyRequest request) {
        try {
            // 4. 한번 더 캐시 확인 (락 대기 중 다른 요청이 완료했을 수 있음)
            Optional<String> cachedResult = idempotencyService.getCachedResult(idempotencyKey);
            if (cachedResult.isPresent()) {
                idempotencyService.releaseLock(idempotencyKey);
                return ResponseEntity.ok(cachedResult.get());
            }

            // 5. 결제 검증 비즈니스 로직 실행
            // TODO: 테스트 완료 후 삭제 - 동시 요청 테스트용 5초 지연
            try {
                log.info("⏳ [TEST] 5초 지연 시작 - idempotencyKey: {}", idempotencyKey);
                Thread.sleep(5000);
                log.info("⏳ [TEST] 5초 지연 완료 - idempotencyKey: {}", idempotencyKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // TODO: 테스트 완료 후 삭제 - Mock 모드 (imp_uid가 mock_으로 시작하면 검증 스킵)
            if (request.getImp_uid().startsWith("mock_")) {
                log.info("🧪 [MOCK] 결제 검증 스킵 - idempotencyKey: {}", idempotencyKey);
            } else {
                paymentService.verifyPayment(request.getImp_uid(), request.getMerchant_uid());
            }
            String result = "Payment Verified Successfully";

            // 6. 결과 캐싱 (24시간 TTL)
            idempotencyService.cacheResult(idempotencyKey, result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Payment verification failed for idempotency key: {}", idempotencyKey, e);
            throw e;
        } finally {
            // 7. 락 해제 (성공/실패 모두)
            idempotencyService.releaseLock(idempotencyKey);
        }
    }

    /**
     * 락 획득 실패 시 대기 후 캐시된 결과 반환 (블로그 방식 핵심!)
     */
    private ResponseEntity<String> waitAndReturnCachedResult(String idempotencyKey) {
        log.info("Lock acquisition failed. Waiting for cached result: {}", idempotencyKey);

        Optional<String> cachedResult = idempotencyService.waitForResultOrTimeout(idempotencyKey);

        if (cachedResult.isPresent()) {
            log.info("Got cached result after waiting: {}", idempotencyKey);
            return ResponseEntity.ok(cachedResult.get());
        }

        // 타임아웃 또는 처리 실패 - 클라이언트에게 재시도 요청
        log.warn("Timeout or processing failed. Retry required: {}", idempotencyKey);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Request processing timed out. Please retry.");
    }
}
