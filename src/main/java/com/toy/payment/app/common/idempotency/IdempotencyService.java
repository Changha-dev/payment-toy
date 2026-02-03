package com.toy.payment.app.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 멱등성(Idempotency) 처리를 위한 서비스 (블로그 방식)
 * 
 * <h2>동작 흐름</h2>
 * 
 * <pre>
 * 1. 락 획득 시도 (SET idempotency_lock:{key} "locked" EX 20 NX)
 * 2. 락 획득 성공 → 캐시 확인 → 비즈니스 로직 → 결과 캐싱 → 락 해제
 * 3. 락 획득 실패 → 대기 → 캐시된 결과 반환
 * </pre>
 * 
 * <h2>키 구조</h2>
 * <ul>
 * <li>idempotency_lock:{key} - 분산 락 (20초 TTL)</li>
 * <li>idempotency_result:{key} - 처리 결과 캐시 (24시간 TTL)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "idempotency_lock:";
    private static final String RESULT_PREFIX = "idempotency_result:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(20);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final int MAX_RETRY = 10;
    private static final long RETRY_INTERVAL_MS = 500;

    /**
     * 락 획득 시도 (블로그 방식)
     * 
     * @param idempotencyKey 클라이언트가 전달한 고유 키
     * @return 락 획득 성공 여부
     */
    public boolean tryAcquireLock(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        // SET NX EX: 키가 없을 때만 설정 + TTL
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", LOCK_TTL);

        if (Boolean.TRUE.equals(success)) {
            log.info("Lock acquired for idempotency key: {}", idempotencyKey);
            return true;
        }
        return false;
    }

    /**
     * 락 해제
     * 
     * @param idempotencyKey 클라이언트가 전달한 고유 키
     */
    public void releaseLock(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        redisTemplate.delete(lockKey);
        log.info("Lock released for idempotency key: {}", idempotencyKey);
    }

    /**
     * 캐싱된 결과 확인
     * 
     * @param idempotencyKey 클라이언트가 전달한 고유 키
     * @return 이미 처리된 경우 저장된 결과, 아니면 empty
     */
    public Optional<String> getCachedResult(String idempotencyKey) {
        String resultKey = RESULT_PREFIX + idempotencyKey;
        String result = redisTemplate.opsForValue().get(resultKey);

        if (result != null) {
            log.info("Cached result found for idempotency key: {}", idempotencyKey);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * 처리 완료된 결과 캐싱
     * 
     * @param idempotencyKey 클라이언트가 전달한 고유 키
     * @param result         처리 결과 (JSON 등)
     */
    public void cacheResult(String idempotencyKey, String result) {
        String resultKey = RESULT_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(resultKey, result, RESULT_TTL);
        log.info("Result cached for idempotency key: {} with result: {}", idempotencyKey, result);
    }

    /**
     * 락 획득 대기 후 캐시된 결과 반환 (블로그 방식 핵심!)
     * 
     * <p>
     * 다른 요청이 처리 중일 때, 락이 해제될 때까지 대기한 후
     * 캐시된 결과를 반환합니다.
     * </p>
     * 
     * @param idempotencyKey 클라이언트가 전달한 고유 키
     * @return 캐시된 결과 또는 empty (타임아웃 시)
     */
    public Optional<String> waitForResultOrTimeout(String idempotencyKey) {
        log.info("Waiting for lock release: {}", idempotencyKey);

        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }

            // 1. 먼저 캐시된 결과가 있는지 확인
            Optional<String> cachedResult = getCachedResult(idempotencyKey);
            if (cachedResult.isPresent()) {
                log.info("Got cached result after waiting for idempotency key: {}", idempotencyKey);
                return cachedResult;
            }

            // 2. 락이 해제되었는지 확인 (처리 실패로 인한 해제)
            String lockKey = LOCK_PREFIX + idempotencyKey;
            if (Boolean.FALSE.equals(redisTemplate.hasKey(lockKey))) {
                log.warn("Lock released but no cached result for idempotency key: {}", idempotencyKey);
                return Optional.empty(); // 처리 실패한 경우 - 새로 시도 가능
            }
        }

        log.warn("Timeout waiting for idempotency key: {}", idempotencyKey);
        return Optional.empty();
    }
}
