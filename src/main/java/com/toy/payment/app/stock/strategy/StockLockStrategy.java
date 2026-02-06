package com.toy.payment.app.stock.strategy;

/**
 * 재고 감소 전략 인터페이스
 * 
 * 4가지 전략을 동일한 인터페이스로 추상화하여 비교 테스트 가능
 */
public interface StockLockStrategy {

    /**
     * 재고 감소 시도
     * 
     * @param productId 상품 ID
     * @param quantity  감소할 수량
     * @return 성공 여부
     */
    boolean decreaseStock(Long productId, Long quantity);

    /**
     * 전략 이름 반환 (로깅/디버깅용)
     */
    String getStrategyName();
}
