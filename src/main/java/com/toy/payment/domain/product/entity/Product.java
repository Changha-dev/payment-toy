package com.toy.payment.domain.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long price;

    private Long stock;

    @Version
    @Builder.Default
    private Long version = 0L;

    public void decreaseStock(Long quantity) {
        if (this.stock < quantity) {
            throw new IllegalArgumentException("Out of stock");
        }
        this.stock -= quantity;
    }
}
