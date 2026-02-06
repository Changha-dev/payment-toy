package com.toy.payment.domain.stock.entity;

/**
 * 재고 슬롯 상태
 * - AVAILABLE: 구매 가능
 * - RESERVED: 예약됨 (결제 진행 중)
 * - SOLD: 판매 완료
 */
public enum StockSlotStatus {
    AVAILABLE,
    RESERVED,
    SOLD
}
