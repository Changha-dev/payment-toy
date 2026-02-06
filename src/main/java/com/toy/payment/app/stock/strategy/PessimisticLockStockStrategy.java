package com.toy.payment.app.stock.strategy;

import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전략 1: DB 비관적 락 (Pessimistic Lock)
 * 
 * SELECT ... FOR UPDATE로 해당 Row에 배타적 락을 획득.
 * 다른 트랜잭션은 락이 해제될 때까지 대기(Blocking).
 * 
 * 장점: 구현 간단, DB만으로 정합성 보장
 * 단점: 단일 Row에 대한 병목 발생, 대기 시간 증가
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PessimisticLockStockStrategy implements StockLockStrategy {

    private final ProductRepository productRepository;

    @Override
    @Transactional
    public boolean decreaseStock(Long productId, Long quantity) {
        try {
            // SELECT ... FOR UPDATE (다른 트랜잭션은 대기)
            Product product = productRepository.findByIdWithPessimisticLock(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            if (product.getStock() < quantity) {
                log.info("[Pessimistic] 재고 부족 - productId: {}, stock: {}", productId, product.getStock());
                return false;
            }

            product.decreaseStock(quantity);
            log.debug("[Pessimistic] 재고 감소 성공 - productId: {}, remaining: {}", productId, product.getStock());
            return true;

        } catch (Exception e) {
            log.error("[Pessimistic] 재고 감소 실패 - productId: {}", productId, e);
            return false;
        }
    }

    @Override
    public String getStrategyName() {
        return "PESSIMISTIC_LOCK";
    }
}
