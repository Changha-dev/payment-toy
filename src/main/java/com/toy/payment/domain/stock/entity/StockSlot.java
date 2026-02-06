package com.toy.payment.domain.stock.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 재고 슬롯 엔티티 (Skip Locked 전략용)
 * 
 * 재고 수만큼 Row를 생성하여, 각 Row를 개별적으로 선점(Lock)하는 방식.
 * SELECT FOR UPDATE SKIP LOCKED 쿼리로 대기 없이 즉시 슬롯 획득/실패 판단 가능.
 */
@Entity
@Table(name = "stock_slot", indexes = {
        @Index(name = "idx_stock_slot_product_status", columnList = "product_id, status")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StockSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockSlotStatus status;

    private LocalDateTime reservedAt;

    private Long userId;

    /**
     * 슬롯 예약 (AVAILABLE -> RESERVED)
     */
    public void reserve(Long userId) {
        if (this.status != StockSlotStatus.AVAILABLE) {
            throw new IllegalStateException("Slot is not available");
        }
        this.status = StockSlotStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
        this.userId = userId;
    }

    /**
     * 슬롯 확정 (RESERVED -> SOLD)
     */
    public void confirm() {
        if (this.status != StockSlotStatus.RESERVED) {
            throw new IllegalStateException("Slot is not reserved");
        }
        this.status = StockSlotStatus.SOLD;
    }

    /**
     * 슬롯 해제 (RESERVED -> AVAILABLE)
     */
    public void release() {
        if (this.status != StockSlotStatus.RESERVED) {
            throw new IllegalStateException("Slot is not reserved");
        }
        this.status = StockSlotStatus.AVAILABLE;
        this.reservedAt = null;
        this.userId = null;
    }
}
