package com.toy.payment.config;

import com.toy.payment.domain.member.entity.Member;
import com.toy.payment.domain.member.repository.MemberRepository;
import com.toy.payment.domain.product.entity.Product;
import com.toy.payment.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
public class DataInit implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (productRepository.count() == 0) {
            productRepository.save(new Product("Limited Edition Sneaker", 150L, 100L));
            productRepository.save(new Product("Vintage Watch", 500L, 5L));
        }

        if (memberRepository.count() == 0) {
            memberRepository.save(new Member("Test User", "test@example.com", "Seoul, Korea"));
        }
    }
}
