# semi_food RPA TODO 리스트

이 문서는 `semi_food` 프로젝트의 RPA 구현 요구사항을 AI가 덜 헷갈리도록 정리한 기준 문서입니다.

## 0. 기준 정보
- 이 문서는 구현 아이디어 메모가 아니라, 실제 코딩 작업을 위한 요구사항 정리 문서다.
- RPA 상태의 기준은 `src/main/resources/db/migration/V6__create_rpa_log_and_audit_tables.sql`이다.
- `rpa_log.status` 값은 `RUNNING`, `COMPLETED`, `FAILED`를 사용한다.
- `rpa_log`의 주요 컬럼은 `started_at`, `ended_at`, `keyword_count`, `product_count`, `message`다.
- RPA 로그 파일 저장 위치는 `src/main/resources/static/test/rpa/log`다.
- 로그 파일명 규칙은 `rpa_parsing_yymmdd_time.log`다.

## 1. RPA 파싱 시퀀스
### 1-1. 목적
- 당일 트렌드 키워드를 저장한 뒤, 해당 키워드를 기준으로 공급자와 상품을 순차 저장한다.

### 1-2. 처리 순서
1. 트렌드 키워드 저장 API를 호출한다.
   - 예시: `http://localhost:8080/api/TrandKeywords/saveWithSequentialId`
   - 저장 건수는 `size = 20` 기준이다.
2. 최근 저장된 `TrendKeyword` 20건을 기준으로 공급자/상품 저장 API를 20회 반복 호출한다.
   - 예시: `http://localhost:8080/api/Products/saveWithSequentialId?keywordId=160&rankId=2179193963&syncDate=20260428`
   - 반복 시 조합할 값은 `keywordId`, `rankId`, `syncDate`다.
3. 실행 시점의 날짜와 트렌드 순위를 함께 기록한다.
4. 제품과 공급자 데이터는 매번 달라질 수 있으므로, 실행 시점 기준의 최신 데이터를 사용한다.

### 1-3. 구현 원칙
- `TrendKeyword -> Supplier -> Product` 순서로 처리한다.
- 당일 파싱한 `TrendKeyword` 목록을 기준으로 반복문을 돌린다.
- 중단 재개를 고려해 각 단계의 진행 상황을 기록한다.
- 하드코딩된 값은 예시로만 사용하고, 실제 구현에서는 동적으로 조회한다.

### 1-4. 로그 저장 원칙
- DB에는 `message`를 통해 어떤 데이터가 저장되었는지 요약 기록한다.
- 파일 로그에는 RPA 실행 시점, 저장된 데이터, 디버깅 로그를 상세히 기록한다.
- DB 로그와 파일 로그의 역할을 분리한다.
  - DB 로그: 운영 조회와 상태 추적
  - 파일 로그: 디버깅과 상세 추적

## 2. 관리자 대시보드
### 2-1. 화면 목적
- `admin` 로그인 시에만 보이는 `rpa/dashboard.html` 화면을 구현한다.
- 화면은 동적 페이지를 기본으로 하고 CRUD API로 데이터를 조회한다.

### 2-2. 화면 구성
- 한 화면에 4개의 뷰를 둔다.
  - `trend_keyword`
  - `supplier`
  - `product`
  - `rpa_log`
- 각 뷰는 설정한 날짜 범위의 데이터만 보여준다.
- 각 뷰 내부는 스크롤 가능해야 한다.

### 2-3. 제어 규칙
- 각 뷰 상단에는 기본 CRUD 버튼을 둔다.
- `rpa_log.status`가 `RUNNING`일 때는 수정/삭제가 잠기도록 한다.
- 강제 잠금 해제(Unlock) 버튼을 제공해 기아 상태를 수동 복구할 수 있게 한다.

### 2-4. 실시간 반영
- SSE(Server-Sent Events)를 사용해 RPA 실행 중 로그를 실시간 갱신한다.
- SSE(Server-Sent Events)를 사용해 DB 반영 데이터를 실시간으로 갱신한다.

## 3. CRUD 범위와 안전 규칙
- `trend_keyword`, `supplier`, `product` 각각에 대한 CRUD를 구현한다.
- 각 Repository에는 당일 기준 조회 메서드를 둔다.
  - 예시: `findAllByCreatedAtGreaterThanEqual(LocalDateTime start)`
- 당일 기준 삭제 메서드를 둔다.
  - 예시: `deleteByCreatedAtGreaterThanEqual(LocalDateTime start)`
- 삭제 순서는 종속성을 고려해 `product -> supplier -> trend_keyword` 순서로 처리한다.
- 파싱 순서는 종속성을 고려해 `trend_keyword -> supplier -> product` 순서로 처리한다.
- `RUNNING` 상태일 때는 수정/삭제 요청을 막는 가드 로직이 필요하다.

## 4. 구현 방식
- 비동기 처리에는 `@Async`를 사용해 웹 요청과 실제 RPA 실행을 분리한다.
- 중복 실행 방지를 위해 낙관적 락(`@Version`)을 검토한다.
- 외부 사이트 호출은 실패 가능성이 있으므로 재시도 전략을 둔다.
- 예외 발생 여부와 관계없이 종료 처리가 되도록 `try-finally` 구조를 사용한다.
- 장시간 `RUNNING` 상태가 유지되면 스케줄러가 강제 초기화할 수 있어야 한다.

## 5. 복구 및 고도화
- `@Scheduled`를 사용해 장시간 `RUNNING` 상태인 데이터를 찾아 복구한다.
- 외부 사이트의 일시적 오류에 대비해 3회 내외 재시도 로직을 둔다.
- 실패 알림은 메일 또는 슬랙 같은 관리자 채널로 확장 가능하게 설계한다.
- 알림 이력과 재처리 이력을 남길 수 있는 구조를 검토한다.

## 6. 작업 우선순위
1. `rpa_log` 기준 상태와 로그 구조를 확정한다.
2. 파싱 RPA 시퀀스를 먼저 구현한다.
3. 관리자 대시보드를 구현한다.
4. CRUD 안전 장치와 복구 로직을 추가한다.
5. SSE 실시간 반영과 알림 기능을 확장한다.

## 7. 구현 시 주의사항
- `TrandKeywords` 같은 오타는 사용하지 말고 `TrendKeyword`로 통일한다.
- 예시 URL의 `localhost`, `keywordId`, `rankId`, `syncDate` 값은 샘플일 뿐이며, 실제 구현은 동적 값 기준으로 작성한다.
- 문서에 나온 모든 항목을 한 번에 완성하려고 하지 말고, 우선순위대로 나눠서 구현한다.
- 이 문서는 설계 방향을 정리한 것이므로, 실제 코드 작성 시에는 현재 엔티티와 마이그레이션 스키마를 우선한다.

