# Bubble Popup
팝업스토어를 직접 운영하는 실시간 경영 시뮬레이션 게임입니다.
위치 선택, 메뉴 구성, 가격 전략, 홍보 액션을 통해 7일간의 시즌을 버티고 최고 매출을 노려보세요.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| 팝업스토어 운영 | 브랜드 네이밍 → 위치 선택 → 준비 → 영업 → 일일 리포트 순으로 진행 |
| 영업 준비 | 메뉴 선택, 판매 가격·수량 설정, 정규 발주 |
| 실시간 영업 | Unity 3D 팝업스토어에서 손님 유입 및 판매 실시간 진행 |
| 액션 시스템 | 영업 중 할인·홍보·긴급발주·나눔 4가지 전략 액션 실행 |
| 이벤트 시스템 | 날씨, 원가 변동, 경제 뉴스 등 랜덤 이벤트가 영업에 영향 |
| 일일 리포트 | 당일 매출·손님 수·평판 결과 확인 및 다음 날 전략 수립 |
| 랭킹 | 시즌 종료 후 전체 플레이어 순위 비교 |
| 뉴스 | 유동인구 순위, 지역 매출 순위 등 게임 내 정보 제공 |
| 파산 시스템 | 3일 연속 적자 또는 임대료 미납 시 파산 |
| 마이페이지 | 닉네임 수정, 시즌 기록 조회 (수익·순위 검색 및 정렬) |
| 튜토리얼 | 처음 플레이하는 유저를 위한 단계별 가이드 |

### 액션 상세

| 액션 | 설명 |
|------|------|
| 할인 | 가격 인하로 고객 점유율 증가 |
| 홍보 | 인플루언서·SNS·전단지·지인 소개 중 선택, 비용과 효과 상이 |
| 긴급 발주 | 재고 부족 시 1.5배 비용으로 즉시 발주 |
| 나눔 | 잉여 재고 기부, 평판 +0.1 상승 |

---

## 기술 스택

**Backend**
- Java 17, Spring Boot
- Spring Security + JWT (Google / SSAFY OAuth2)
- Spring Data JPA, MySQL (Flyway 마이그레이션)
- Redis (게임 상태 캐싱)

**Data**
- Apache Hadoop (HDFS)
- Apache Spark (ETL 파이프라인)

**Frontend**
- React 19, TypeScript, Vite
- Zustand (상태 관리)
- React Router v7, Axios
- Tailwind CSS
- Three.js / React Three Fiber (3D 렌더링)
- Unity WebGL (팝업스토어 씬)

---

## 아키텍처

```
Bubble-Popup/
├── S14P21A205_BE/    # Spring Boot REST API
│   ├── src/
│   ├── spark/        # Spark ETL 잡
│   ├── data/         # 초기 데이터 및 HDFS 초기화 스크립트
│   ├── monitoring/   # Prometheus / Grafana 설정
│   └── ops/          # 배포 스크립트, Nginx, systemd
├── S14P21A205_FE/    # React SPA
├── seed/             # DB 시드 데이터
├── nginx/            # 외부 Nginx 설정
└── scripts/          # 운영 스크립트
```

**게임 플로우**

```
로그인 → 브랜드 네이밍 → 대기실 → [시즌 시작]
  └─ 위치 선택 → 준비(40s) → 영업(120s) → 리포트(20s)  × 7일
       └─ 파산 시 조기 종료                → 시즌 랭킹
```

---

## 시작하기

### 사전 요구사항

- Java 17+
- Node.js 20+
- Docker (MySQL / Redis / Hadoop / Spark)

### Backend

```bash
cd S14P21A205_BE

# .env 파일 생성 후 아래 값 입력
# GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
# SSAFY_CLIENT_ID, SSAFY_CLIENT_SECRET
# SSAFY_AUTHORIZATION_URI, SSAFY_TOKEN_URI, SSAFY_USER_INFO_URI
# JWT_SECRET

./gradlew bootRun
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### Frontend

```bash
cd S14P21A205_FE
npm install
npm run dev
```
