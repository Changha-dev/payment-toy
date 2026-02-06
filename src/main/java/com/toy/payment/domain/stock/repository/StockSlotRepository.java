package com.toy.payment.domain.stock.repository;

import com.toy.payment.domain.stock.entity.StockSlot;
import com.toy.payment.domain.stock.entity.StockSlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockSlotRepository extends JpaRepository<StockSlot, Long> {

    /**
     * Skip Locked 쿼리: 잠겨있지 않은 첫 번째 AVAILABLE 슬롯을 선점
     * 
     * - FOR UPDATE: 해당 Row에 배타적 락 획득
     * - SKIP LOCKED: 이미 다른 트랜잭션이 락을 잡고 있으면 건너뜀 (대기 X)
     * - LIMIT 1: 하나의 슬롯만 반환
     */
    @Query(value = """
            SELECT * FROM stock_slot
            WHERE product_id = :productId
            AND status = 'AVAILABLE'
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<StockSlot> findFirstAvailableSlotWithSkipLock(@Param("productId") Long productId);

    /**
     * 특정 상품의 AVAILABLE 슬롯 개수 조회 (재고 확인용)
     */
    long countByProductIdAndStatus(Long productId, StockSlotStatus status);

    /**
     * 특정 상품의 모든 슬롯 삭제 (테스트 초기화용)
     */
    @Modifying
    @Query("DELETE FROM StockSlot s WHERE s.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
