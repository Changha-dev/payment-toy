package com.toy.payment.app.stock.controller;

import com.toy.payment.app.stock.strategy.*;
import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 재고 락 전략 벤치마크 컨트롤러
 * 
 * 4가지 전략을 동일한 API로 테스트하여 성능과 정합성 비교
 */
@Slf4j
@RestController
@RequestMapping("/api/benchmark/stock")
@RequiredArgsConstructor
public class StockBenchmarkController {

    private final PessimisticLockStockStrategy pessimisticLockStrategy;
    private final RedisLockStockStrategy redisLockStrategy;
    private final RedisDecrStockStrategy redisDecrStrategy;
    private final SkipLockedStockStrategy skipLockedStrategy;
    private final ProductRepository productRepository;

    /**
     * 전략별 재고 감소 API
     * 
     * @param strategy  전략 이름 (pessimistic, redis-lock, redis-decr, skip-locked)
     * @param productId 상품 ID
     * @param quantity  감소 수량 (기본값: 1)
     */
    @PostMapping("/{strategy}")
    public ResponseEntity<Map<String, Object>> decreaseStock(
            @PathVariable String strategy,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") Long quantity) {

        long startTime = System.currentTimeMillis();
        boolean success;

        switch (strategy.toLowerCase()) {
            case "pessimistic":
                success = pessimisticLockStrategy.decreaseStock(productId, quantity);
                break;
            case "redis-lock":
                success = redisLockStrategy.decreaseStock(productId, quantity);
                break;
            case "redis-decr":
                success = redisDecrStrategy.decreaseStock(productId, quantity);
                break;
            case "skip-locked":
                success = skipLockedStrategy.decreaseStock(productId, quantity);
                break;
            default:
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unknown strategy: " + strategy));
        }

        long duration = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(Map.of(
                "strategy", strategy,
                "productId", productId,
                "quantity", quantity,
                "success", success,
                "durationMs", duration));
    }

    /**
     * 테스트용 재고 초기화 API
     * 
     * @param productId 상품 ID
     * @param stock     초기 재고
     */
    @PostMapping("/init/{productId}")
    public ResponseEntity<Map<String, Object>> initStock(
            @PathVariable Long productId,
            @RequestParam Long stock) {

        // 1. DB 재고 초기화
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // 새 Product 엔티티로 교체하여 stock 업데이트
        Product updatedProduct = Product.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(stock)
                .version(product.getVersion())
                .build();
        productRepository.save(updatedProduct);

        // 2. Redis DECR 전략용 재고 초기화
        redisDecrStrategy.initRedisStock(productId, stock);

        // 3. Skip Locked 전략용 슬롯 초기화
        skipLockedStrategy.initSlots(productId, stock);

        log.info("재고 초기화 완료 - productId: {}, stock: {}", productId, stock);

        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "stock", stock,
                "message", "Stock initialized for all strategies"));
    }

    /**
     * 현재 재고 상태 조회
     */
    @GetMapping("/status/{productId}")
    public ResponseEntity<Map<String, Object>> getStockStatus(@PathVariable Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Long redisStock = redisDecrStrategy.getRedisStock(productId);
        long availableSlots = skipLockedStrategy.getAvailableSlotCount(productId);

        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "dbStock", product.getStock(),
                "redisStock", redisStock != null ? redisStock : "N/A",
                "availableSlots", availableSlots));
    }
}
