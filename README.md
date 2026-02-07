# Payment Domain Toy Project

> PG 결제 도메인을 직접 구현하며, **동시성 제어**와 **데이터 정합성** 문제를 해결한 과정을 기록한 프로젝트입니다.

## Tech Stack
- **Backend**: Java 21, Spring Boot 3.x, JPA
- **Database**: MySQL 8.0, Redis
- **Infra**: Docker, Docker Compose

---

## 주요 구현 사항

### 1. 결제 시 멱등성 보장 및 결제 불일치 상황 해결
> [상세 블로그 글 보기](https://changha-dev.tistory.com/202)

- 네트워크 지연이나 사용자의 중복 클릭으로 인한 **결제 요청 중복 발생 가능성** 확인
- **Redis 기반 분산 락** 도입으로 동시 요청 제어 및 TTL 시간 설정 고민
- **Idempotency Key 패턴 적용**: 요청 헤더에 고유 키를 포함시켜 중복 처리를 원천 차단
- **Fail-Fast 전략**: 락 획득 실패 시 대기하지 않고 즉시 `409 Conflict`를 반환하여 불필요한 서버 리소스 점유 방지
- PG사 통신 중 **Read Timeout** 발생 시, 결제 상태 불일치 상황 해결을 위한 분기 처리 적용

<img width="567" alt="멱등성 보장 아키텍처" src="https://github.com/user-attachments/assets/8b283601-5edb-4897-8319-c5eaec037be6" />

---

### 2. 재고 관리 순서에 따른 동시성 제어 전략
> [상세 블로그 글 보기](https://changha-dev.tistory.com/203)

- **재고 감소 시점**에 따른 비즈니스 로직 차이점 분석 및 전략 수립
  - 일반 쇼핑몰: 결제 후 **낙관적 락** 적용 (충돌 시 자동 환불 처리)
  - 한정 판매: 결제 전 **Skip Locked**로 재고 선점 (대기 없이 즉시 실패 처리)
- **3가지 동시성 전략 구현 및 벤치마크 수행**

| 전략 | 100 Request 평균 응답 시간 | 특징 |
|:---|:---:|:---|
| DB 비관적 락 | 527ms | 순차 처리, 데이터 정합성 완벽 |
| Redis DECR (Async) | 34ms | 최고 속도, 관리 복잡도 증가 |
| **Skip Locked** | **241ms** | 병렬 처리, 추가 인프라 불필요 |

- **결론**: 추가 인프라 없이 **Skip Locked**로 응답 속도 **2.2배 개선** (527ms → 241ms)

<img width="1018" alt="재고 동시성 전략 비교" src="https://github.com/user-attachments/assets/8ae7d9e4-8b72-4bc6-b920-6d18fcce86ca" />

---

## 실행 방법

```bash
# Docker로 MySQL, Redis 실행
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

---

## 프로젝트 구조

```
src/main/java/com/toy/payment/
├── app/
│   ├── payment/      # 결제 서비스 (멱등성, 분산락)
│   └── stock/        # 재고 관리 (동시성 전략)
├── domain/
│   ├── product/      # 상품 엔티티
│   └── stock/        # 재고 슬롯 엔티티 (Skip Locked)
└── config/           # Redis, JPA 설정
```
