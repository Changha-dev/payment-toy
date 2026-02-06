package com.toy.payment.app.stock.strategy;

import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전략 3: Redis DECR (원자적 감소)
 * 
 * Redis의 DECR 명령어로 원자적으로 재고 감소.
 * 가장 빠른 방식이지만 Redis-DB 동기화 필요.
 * 
 * 장점: 가장 빠름, 락 없이 원자적 처리
 * 단점: Redis-DB 동기화 필요, Redis 재시작 시 데이터 유실 위험
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDecrStockStrategy implements StockLockStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    @Override
    @Transactional
    public boolean decreaseStock(Long productId, Long quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;

        try {
            // 1. Redis에서 원자적으로 재고 감소 (DECRBY)
            Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, quantity);

            if (remainingStock == null || remainingStock < 0) {
                // 재고 부족 - 원복 (INCRBY)
                if (remainingStock != null) {
                    redisTemplate.opsForValue().increment(stockKey, quantity);
                }
                log.info("[RedisDecr] 재고 부족 - productId: {}, attempted: {}", productId, quantity);
                return false;
            }

            // 2. DB 동기화 (비관적 락으로 충돌 방지)
            Product product = productRepository.findByIdWithPessimisticLock(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
            product.decreaseStock(quantity);
            // save 호출 불필요 - 영속성 컨텍스트가 변경 감지

            log.debug("[RedisDecr] 재고 감소 성공 - productId: {}, redisStock: {}", productId, remainingStock);
            return true;

        } catch (Exception e) {
            log.error("[RedisDecr] 재고 감소 실패 - productId: {}", productId, e);
            // 실패 시 Redis 원복
            redisTemplate.opsForValue().increment(stockKey, quantity);
            return false;
        }
    }

    /**
     * Redis 재고 초기화 (테스트용)
     */
    public void initRedisStock(Long productId, Long stock) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        log.info("[RedisDecr] Redis 재고 초기화 - productId: {}, stock: {}", productId, stock);
    }

    /**
     * Redis 재고 조회
     */
    public Long getRedisStock(Long productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String value = redisTemplate.opsForValue().get(stockKey);
        return value != null ? Long.parseLong(value) : null;
    }

    @Override
    public String getStrategyName() {
        return "REDIS_DECR";
    }
}
