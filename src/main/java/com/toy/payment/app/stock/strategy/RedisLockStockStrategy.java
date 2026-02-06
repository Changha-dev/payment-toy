package com.toy.payment.app.stock.strategy;

import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 전략 2: Redis 분산 락 (Distributed Lock)
 * 
 * Redis로 락을 획득한 후 DB 재고 감소.
 * 락 획득 실패 시 즉시 반환 (Fail-Fast).
 * 
 * 장점: DB 부하 분산, 빠른 락 판단
 * 단점: Redis-DB 간 정합성 별도 관리 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockStockStrategy implements StockLockStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    private static final String LOCK_PREFIX = "stock_lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    @Override
    @Transactional
    public boolean decreaseStock(Long productId, Long quantity) {
        String lockKey = LOCK_PREFIX + productId;

        // 1. Redis 락 획득 시도 (SET NX EX)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[RedisLock] 락 획득 실패 - productId: {}", productId);
            return false;
        }

        try {
            // 2. DB에서 재고 감소
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            if (product.getStock() < quantity) {
                log.info("[RedisLock] 재고 부족 - productId: {}, stock: {}", productId, product.getStock());
                return false;
            }

            product.decreaseStock(quantity);
            productRepository.save(product);
            log.debug("[RedisLock] 재고 감소 성공 - productId: {}, remaining: {}", productId, product.getStock());
            return true;

        } catch (Exception e) {
            log.error("[RedisLock] 재고 감소 실패 - productId: {}", productId, e);
            return false;
        } finally {
            // 3. 락 해제
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public String getStrategyName() {
        return "REDIS_DISTRIBUTED_LOCK";
    }
}
