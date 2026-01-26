#!/bin/bash

# 1. Git Init
git init
echo "Git initialized."

# 2. Secure Keys
if ! grep -q "application.yml" .gitignore; then
    echo "src/main/resources/application.yml" >> .gitignore
    echo "Added application.yml to .gitignore for security."
fi

# 3. Stage All Files
git add .

# 4. Unstage 'Fix' files to separate them (for the second commit)
# These are the files we worked on for the PortOne V1 fix
git rm --cached src/main/java/com/toy/payment/app/payment/service/PaymentService.java
git rm --cached src/main/java/com/toy/payment/app/payment/service/PortOneService.java
git rm --cached src/main/resources/templates/product/detail.html
git rm --cached src/test/java/com/toy/payment/app/payment/service/PaymentServiceTest.java

# 5. Commit 1: Initial Setup
git commit -m "feat: 초기 프로젝트 구조 및 기본 설정 구성

## Summary
Spring Boot 기반 결제 시스템 토이 프로젝트 초기 구성

## Changes
- Spring Boot 3.4, Java 17 환경 설정
- MySQL, Redis, JPA 연동 설정
- 상품(Product), 주문(Order), 결제(Payment) 도메인 엔티티 및 기본 로직 구현"

# 6. Stage 'Fix' files again
git add src/main/java/com/toy/payment/app/payment/service/PaymentService.java
git add src/main/java/com/toy/payment/app/payment/service/PortOneService.java
git add src/main/resources/templates/product/detail.html
git add src/test/java/com/toy/payment/app/payment/service/PaymentServiceTest.java

# 7. Commit 2: PortOne V1 Fix
git commit -m "fix: PortOne V1 결제 시스템 연동 및 검증 로직 구현

## Summary
PortOne V2 API 연동 이슈(계정 불일치) 해결 및 V1 Fallback 검증 로직 추가

## Changes
- PortOneService: V1 API 토큰 발급 및 결제 조회 구현 (merchant_uid Fallback 추가)
- PaymentService: 결제 검증 로직 개선 (imp_uid/merchant_uid 이중 조회)
- Frontend: PortOne V1 SDK 연동 및 캐시 이슈 대응
- Test: PaymentService 검증 로직 단위 테스트 추가"

echo "✅ Git setup and split commits completed successfully!"
echo "Now you can add your remote origin and push:"
echo "git remote add origin <YOUR_REPO_URL>"
echo "git push -u origin main"
