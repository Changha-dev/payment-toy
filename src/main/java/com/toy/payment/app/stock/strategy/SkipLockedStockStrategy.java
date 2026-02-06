package com.toy.payment.app.stock.strategy;

import com.toy.payment.domain.stock.entity.StockSlot;
import com.toy.payment.domain.stock.entity.StockSlotStatus;
import com.toy.payment.domain.stock.repository.StockSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 전략 4: Skip Locked (재고 슬롯 방식)
 * 
 * 재고 수만큼 Row를 생성하고, SELECT FOR UPDATE SKIP LOCKED로 선점.
 * 이미 락이 걸린 Row는 건너뛰므로 대기 없이 즉시 성공/실패 판단.
 * 
 * 장점: 완벽한 병렬성, Fail-Fast
 * 단점: 테이블 Row 증가, 슬롯 관리 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipLockedStockStrategy implements StockLockStrategy {

    private final StockSlotRepository stockSlotRepository;

    @Override
    @Transactional
    public boolean decreaseStock(Long productId, Long quantity) {
        try {
            // quantity만큼 슬롯 선점 시도
            List<StockSlot> reservedSlots = new ArrayList<>();

            for (int i = 0; i < quantity; i++) {
                // SKIP LOCKED: 이미 잠긴 슬롯은 건너뜀 (대기 X)
                Optional<StockSlot> slotOpt = stockSlotRepository.findFirstAvailableSlotWithSkipLock(productId);

                if (slotOpt.isEmpty()) {
                    // 가용 슬롯 없음 - 이미 선점한 슬롯 해제
                    reservedSlots.forEach(StockSlot::release);
                    log.info("[SkipLocked] 슬롯 부족 - productId: {}, reserved: {}/{}",
                            productId, reservedSlots.size(), quantity);
                    return false;
                }

                StockSlot slot = slotOpt.get();
                slot.reserve(null); // userId는 실제 구현 시 전달
                reservedSlots.add(slot);
            }

            // 모든 슬롯 선점 성공 -> SOLD로 확정
            reservedSlots.forEach(StockSlot::confirm);
            log.debug("[SkipLocked] 재고 감소 성공 - productId: {}, slots: {}", productId, quantity);
            return true;

        } catch (Exception e) {
            log.error("[SkipLocked] 재고 감소 실패 - productId: {}", productId, e);
            return false;
        }
    }

    /**
     * 재고 슬롯 초기화 (테스트용)
     * 기존 슬롯 삭제 후 새로 생성
     */
    @Transactional
    public void initSlots(Long productId, Long stock) {
        // 기존 슬롯 삭제
        stockSlotRepository.deleteByProductId(productId);

        // 새 슬롯 생성
        List<StockSlot> slots = new ArrayList<>();
        for (int i = 0; i < stock; i++) {
            slots.add(StockSlot.builder()
                    .productId(productId)
                    .status(StockSlotStatus.AVAILABLE)
                    .build());
        }
        stockSlotRepository.saveAll(slots);
        log.info("[SkipLocked] 슬롯 초기화 완료 - productId: {}, slots: {}", productId, stock);
    }

    /**
     * 가용 슬롯 개수 조회
     */
    public long getAvailableSlotCount(Long productId) {
        return stockSlotRepository.countByProductIdAndStatus(productId, StockSlotStatus.AVAILABLE);
    }

    @Override
    public String getStrategyName() {
        return "SKIP_LOCKED";
    }
}
