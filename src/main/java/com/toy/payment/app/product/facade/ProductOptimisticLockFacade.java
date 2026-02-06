package com.toy.payment.app.product.facade;

import com.toy.payment.app.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductOptimisticLockFacade {

    private final ProductService productService;

    public void decreaseStock(Long id, long quantity) throws InterruptedException {
        while (true) {
            try {
                productService.decreaseStock(id, quantity);
                break;
            } catch (OptimisticLockingFailureException e) {
                log.info("Optimistic lock conflict for product {}. Retrying...", id);
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("Unexpected error during stock decrease for product {}", id, e);
                throw e;
            }
        }
    }
}
