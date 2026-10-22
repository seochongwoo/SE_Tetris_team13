# SE Tetris — Team 13

서울과학기술대학교 소프트웨어공학 팀 프로젝트로 만든 텍스트 기반 테트리스입니다. Java 21과 Swing으로 만들었고, 화면은 글자 격자(44×24)로 그립니다.

- 일반 모드: 20줄 × 10칸 보드, 테트로미노 7종, 난이도 easy / normal / hard
- 아이템 모드: 누적 10줄을 지울 때마다 아이템 블록 등장 ([아이템 명세서](docs/item-specification.md))
- 설정: 화면 크기 3종, 조작 키 변경, 색맹 모드, 난이도, 스코어보드 초기화, 기본값 복원 (다음 실행에도 유지)
- 스코어보드: 모드(일반/아이템) × 난이도별 상위 10개
- 게임 화면: NEXT 위에 현재 모드·난이도 표시, 줄·아이템 삭제 시 300ms 연출

## 실행

### 배포 패키지 (권장)

1. [Releases](https://github.com/seochongwoo/SE_Tetris_team13/releases)에서 `SETetris-<버전>-windows.zip`을 받습니다.
2. 압축을 풀고 `SETetris\SETetris.exe`를 더블클릭합니다.

JRE가 패키지에 들어 있어 Java를 따로 설치하지 않아도 됩니다. 대상 OS는 Windows 11입니다.

### 소스에서 실행

Java 21(JDK)이 필요합니다.

```powershell
.\gradlew.bat run
```

설정과 기록은 `%USERPROFILE%\.se-tetris-team13` 폴더에 저장됩니다.

개발용 디버그 모드는 `.\gradlew.bat run --args="--debug"`로 실행합니다. 보드·스탯·다음 블록을 직접 정해 아이템 동작을 재현할 수 있으며, 설정과 기록은 `debug` 하위 폴더에 따로 저장됩니다 ([개발 가이드](docs/developer-guide.md) 2장 "디버그 모드").

## 조작

| 동작 | 기본 키 |
| --- | --- |
| 왼쪽 / 오른쪽 이동 | ← / → |
| 아래로 한 칸 | ↓ |
| 시계 방향 회전 | ↑ |
| 한 번에 떨어뜨리기 | Space |
| 일시정지 / 재개 | P |
| 게임 메뉴 (재개 / 메뉴로 / 프로그램 종료) | Esc |

- 메뉴는 ↑↓로 이동하고 Enter로 선택합니다. 화면마다 사용할 수 있는 키가 아래쪽에 표시됩니다.
- 게임 조작 키는 설정 화면에서 바꿀 수 있습니다.
- 스코어보드에서 ←→는 난이도, ↑↓는 모드(일반/아이템)를 바꿉니다.

## 게임 규칙 요약

| 항목 | 규칙 |
| --- | --- |
| 낙하 속도 | 처음 1초에 1칸. 10줄마다 레벨이 오르고, 레벨당 easy 80ms / normal 100ms / hard 120ms씩 빨라짐 (최소 100ms) |
| 블록 등장 확률 | 룰렛 휠 선택. 다른 블록 가중치 10일 때 I블록 easy 12 / normal 10 / hard 8 |
| 점수 | 블록이 1칸 떨어질 때마다 (레벨 + 1)점 (자동·수동 무관), 줄 삭제 시 100 × (지운 줄 수)² 추가 |
| 삭제 연출 | 60ms 흰색 강조 → 240ms 동안 중앙부터 양쪽으로 삭제 → 삭제 결과 표시. 폭탄·연쇄는 삭제된 칸만 연출하며 무게추의 칸 삭제는 즉시 적용. 줄 삭제가 있을 때만 위 줄이 내려옴. 연출 중에는 블록이 멈추고, 그동안 누른 키는 보관했다가 연출이 끝나면 순서대로 처리 |
| 게임 종료 | 새 블록을 놓을 수 없으면 종료. 상위 10위 안이면 이름(최대 12자)을 입력하고 순위표에 강조 표시 |

## 빌드와 테스트

```powershell
.\gradlew.bat build jacocoTestReport
```

- 테스트: JUnit 5. 결과는 `build/reports/tests/test/index.html`
- 커버리지: JaCoCo. 결과는 `build/reports/jacoco/test/html/index.html`
- CI: push와 PR마다 GitHub Actions(Windows, Java 21)에서 빌드·테스트·커버리지를 실행합니다.

배포 패키지는 다음 명령으로 만듭니다. 결과는 `build/distributions/SETetris-<버전>-windows.zip`입니다.

```powershell
.\gradlew.bat packageZip
```

## 구조

헥사고날(포트-어댑터) 구조입니다.

| 패키지 | 역할 |
| --- | --- |
| `core` | 테트리스 규칙: 보드, 블록, 회전, 블록 생성, 아이템 효과 |
| `application` | 한 판의 진행: 시간·중력, 점수·속도 정책, 설정·기록 서비스, 종료 흐름 |
| `storage` | 설정(`settings.properties`)과 기록(`scores.bin`) 파일 저장 |
| `bootstrap` | 구성 요소 조립과 실행 진입점 |
| `ui` | Swing 창, 화면 전환, 키 입력, 글자 격자 렌더링 |

## 문서

| 문서 | 내용 |
| --- | --- |
| [아이템 명세서](docs/item-specification.md) | 아이템 모드의 아이템 형태·기능 (평가용) |
| [요구사항 추적표](docs/requirements-traceability.md) | 1·2차 요구사항별 구현 위치, 테스트, 상태 |
| [요구사항별 코드 흐름](docs/requirements-code-map.md) | 요구사항마다 거치는 파일과 메서드 호출 흐름 |
| [프로젝트 관리 규칙](docs/project-management.md) | 브랜치·PR·이슈·작업 보드(GitHub Projects) 운영 규칙 |
| [프로젝트 진행 기록](docs/project-history.md) | 날짜별 결정 사항, 문서 수정, 검토·확인 내역 |
| [평가 준비와 데모 순서](docs/evaluation-guide.md) | 평가 당일 준비물과 시연 순서 |
| [개발 가이드](docs/developer-guide.md) | 구조, API, 확장 방법, 빌드 |
| [아키텍처 결정 기록](docs/architecture-decision.md) | 헥사고날 구조를 고른 이유 |
| [2차 요구사항 분석](docs/requirements2-analysis.md) | 2차 요구사항이 구조에 미치는 영향 |
| [작업 진행 제안](docs/requirements2-work-plan.md) · [백로그 진행 현황](docs/requirements2-backlog.md) | 2차 작업 분할과 진행 상황 |
| [아이템 모드 설계 정리](docs/item-spec.md) | 아이템 규칙 논의와 구현 메모 (팀 내부용) |
| [미확정 사항](docs/open-questions.md) | 요구사항 해석, 팀 논의 안건과 결정 기록 |

## 팀

| 이름 | GitHub | 2차 담당 |
| --- | --- | --- |
| 서종우 | [seochongwoo](https://github.com/seochongwoo) | 카드 단위 분담 ([백로그](docs/requirements2-backlog.md)) |
| 강형준 | [khanwul](https://github.com/khanwul) | 카드 단위 분담 ([백로그](docs/requirements2-backlog.md)) |
