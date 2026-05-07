# 스마트 식품 이커머스 플랫폼 v1.0

식품 트렌드 키워드를 RPA로 수집·분석하여 상품 큐레이션을 자동화하고,
주문 발생 시 자체 발주 시스템까지 연결하는 스마트 이커머스 플랫폼.

---

## Hard Rules (절대 불변)

1. **no hardcoded secrets** — DB 접속 정보, API 키, 토큰 등 모든 자격증명은 환경변수로만. 코드에 직접 작성 절대 금지.
2. **발주 실행 전 확인 단계 필수** — 모든 발주는 기본적으로 사용자 확인 후 실행. 단, 품목별 자동발주 플래그(auto_order = true)가 명시적으로 활성화된 품목은 예외. 자동발주는 기본 OFF.
3. **결측 데이터 → REJECT** — 키워드·가격·재고 데이터 누락 시 추정·보간 금지. 명시적 오류 처리 후 상위 레이어에 전달.
4. **no raw SQL** — TiDB(MySQL) 접근은 JPA 또는 MyBatis 매퍼로만. 사용자 입력을 직접 쿼리에 삽입 금지 (파라미터 바인딩 필수).
5. **입력값 검증 필수** — 모든 사용자 입력 엔드포인트에 @Valid / Bean Validation 적용. Python RPA에서 수집된 외부 데이터도 저장 전 검증.

---

## Quick Ref

| 작업 | 명령어 |
|------|--------|
| 백엔드 실행 | `./gradlew bootRun` |
| 백엔드 테스트 | `./gradlew test` |
| Python RPA 실행 | `python rpa/main.py` |
| Python 테스트 | `pytest rpa/tests/ -q` |
| 프론트 확인 | `http://localhost:8080/login.html` (백엔드 실행 후) |
| DB 마이그레이션 | `./gradlew flywayMigrate` (Flyway 사용 시) |

---

## Secrets Policy

- `.env` 파일은 절대 읽거나 출력하거나 로그에 남기지 않는다.
- `.env`는 절대 커밋하지 않는다 — `.env.example`이 템플릿 (실제 값 없음).
- 새 API 키 추가 시 → `.env.example`에 플레이스홀더 추가 + 코드에서 환경변수로 로드.
- TiDB Cloud 접속 URL은 `TIDB_URL` 환경변수로만 참조.

---

## Dev Conventions

- **테스트 먼저** — 머지 전 테스트 통과 필수. 테스트 없이 완료 선언 금지.
- **기능 플래그** — 새 기능은 환경변수로 opt-in, 기본 OFF. (예: `AUTO_ORDER_ENABLED=0`)
- **로그** — append-only. 기존 로그 파일 덮어쓰기 금지.
- **커밋** — 논리적 단위 1개씩. 독립적으로 revert 가능한 단위로 분리.
- **커밋은 명시적 요청 시에만** — 자동 커밋 금지.
- **API 설계** — 프론트엔드는 DB 데이터만 읽음. 프론트에서 네이버 쇼핑 API 직접 호출 금지.
- **자동발주 플래그** — `auto_order` 컬럼은 품목 테이블에 존재, 기본값 `false`. 변경 이력 반드시 로깅.
- **브랜치 전략** — `main` (배포), `develop` (통합), `feature/`, `fix/` 접두사 사용.

---

## Design System

- **모든 프론트엔드 작업은 `DESIGN.md`를 따른다** — 새 페이지, 컴포넌트, 스타일 수정 시 예외 없이 적용.
- **컬러**: `DESIGN.md` Section 2의 팔레트만 사용. 임의 색상 추가 금지.
- **타이포그래피**: Inter 폰트, Section 3의 크기·굵기·자간 규칙 준수.
- **컴포넌트**: 버튼·카드·인풋·배지는 Section 4 스펙 그대로. 변형 시 사유 명시.
- **레이아웃**: 8px 기준 간격, 최대 너비 1200px, Section 5 화이트스페이스 철학 유지.
- **반응형**: Section 7 브레이크포인트 기준으로 구현.
- `DESIGN.md`에 없는 패턴이 필요하면 → 기존 패턴에서 파생, 임의 스타일 생성 금지.

---

## Compact Instructions

컴팩션 시 반드시 보존:
1. Hard Rules (5개 전부)
2. 현재 활성 브랜치 / 미커밋 파일 목록
3. 진행 중인 태스크 및 상태
4. 현재 조사 중인 오류 또는 버그
5. Dev Conventions
6. 이번 세션에서 수정한 파일 경로

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
