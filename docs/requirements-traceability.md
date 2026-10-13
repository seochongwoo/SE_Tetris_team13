# 요구사항 추적표

1차·2차 요구사항(`TeamProject_Req1.pdf`, `TeamProject_Req2.pdf`)의 항목별로 구현 위치, 검증 테스트, 상태를 정리한 표입니다. 평가 전에 빠진 항목이 없는지 확인하는 체크리스트로 씁니다. 구현이 바뀌면 이 표도 함께 고칩니다.

- 상태: ✅ 충족 / 🔄 진행 중 / ❌ 미구현 / ⚠️ 보완 필요
- 경로는 `src/main/java/team/tetris/` 기준, 테스트는 `src/test/java/team/tetris/` 아래 클래스 이름입니다.
- 마지막 확인: 2026-10-13, `feat/team-items` 브랜치 (테스트 403개 통과, 라인 커버리지 92.6%)

## 1. 1차 요구사항

### 기능

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R1-01 | 실행 시 시작 메뉴, 상단에 게임 이름 (p5) | `ui/screen/MenuScreen` | `MenuScreenTest` | ✅ |
| R1-02 | 게임 시작·설정·스코어보드·종료 메뉴, 선택 시 기능 수행 (p5) | `ui/ScreenRouter.menuItems()` | `ScreenRouterTest` | ✅ |
| R1-03 | 키보드로 메뉴 이동, Enter로 선택, 다른 키 입력 시 사용 가능 키 표시 (p5) | `MenuScreen` | `MenuScreenTest` | ✅ |
| R1-04 | 메뉴를 나중에 추가할 수 있는 설계 (p5) | `ScreenRouter.menuItems()` 목록 한 줄 추가 (2차에서 "아이템 모드" 추가로 확인) | `ScreenRouterTest` | ✅ |
| R1-05 | 20줄 × 10칸 보드 (p6, p7) | `bootstrap/AppComposition` (`PlayerEngine(10, 20, …)`), `core/Board` | `TextRendererTest`, `BoardTest` | ✅ |
| R1-06 | 테트로미노 7종이 같은 확률로 무작위 등장 (p6, p9) | `core/TetrominoType`, `core/rule/WeightedRandomGenerator` (normal은 가중치 동일) | `WeightedRandomGeneratorTest`, `AppCompositionTest` | ✅ |
| R1-07 | 행을 채우면 삭제 (p6) | `core/Board.clearRows` | `BoardTest`, `PlayerEngineTest` | ✅ |
| R1-08 | 화면: 보드, 다음 블록, 실시간 점수 (p7) | `ui/render/TextRenderer` | `TextRendererTest` | ✅ |
| R1-09 | 조작 없이 1초에 1칸 낙하, 줄 삭제에 따라 가속 (p8) | `application/SinglePlayerSession.update`, `application/SpeedPolicy` | `SinglePlayerSessionTest`, `SpeedPolicyTest` | ✅ |
| R1-10 | 좌·우·아래 이동, 시계 방향 90도 회전, 한 번에 떨어뜨리기 (p8) | `core/PlayerEngine`, `core/rule/RotationSystem` | `PlayerEngineTest`, `RotationSystemTest` | ✅ |
| R1-11 | 반복 키 입력이 무시되지 않고 즉시 반응 (p8) | `ui/input/RepeatController`, `ui/screen/GameScreen` | `RepeatControllerTest`, `ResponsivenessTest` | ✅ |
| R1-12 | 일시정지·재개 키 (p8) | `GameScreen` (P 키, Esc 게임 메뉴) | `GameScreenTest` | ✅ |
| R1-13 | 진행·일시정지 중 게임 종료 방법 (OS 기능 제외) (p8) | `GameScreen` 게임 메뉴 "메뉴로 / 프로그램 종료" | `GameScreenTest` | ✅ |
| R1-14 | 블록마다 다른 색, 비슷한·좌우 반전 블록은 대비되는 색·무늬 (p9) | `ui/render/palette/ColorPalette`, `BlockGlyphs` | `ColorPaletteTest` | ✅ |
| R1-15 | 색맹 모드: 적록·청황 색맹도 블록 구분 (p10) | `ColorPalette` (색맹용 팔레트 + 블록별 무늬) | `ColorPaletteTest`, `SettingsScreenTest` | ✅ |
| R1-16 | 1칸 떨어질 때마다 점수 (자동·수동 무관), 실시간 표시 (p11) | `application/ScorePolicy`, `TextRenderer` | `ScorePolicyTest`, `SinglePlayerSessionTest` | ✅ |
| R1-17 | 속도가 빨라지면 추가 점수 (p11) | `ScorePolicy` (떨어진 칸 × (레벨 + 1)) | `ScorePolicyTest` | ✅ |
| R1-18 | 제시되지 않은 점수 방식 1개 이상 (p11) | `ScorePolicy` (줄 삭제 100 × 줄 수²) | `ScorePolicyTest` | ✅ |
| R1-19 | 설정: 화면 크기 3종 이상 (p12) | `ui/screen/SettingsScreen`, `ui/render/TextFramePanel` (작게/보통/크게) | `SettingsScreenTest`, `TextFramePanelTest` | ✅ |
| R1-20 | 설정: 조작 키 변경 (p12) | `SettingsScreen` (중복 키 거부) | `SettingsScreenTest`, `InputMapperTest` | ✅ |
| R1-21 | 설정: 스코어보드 초기화, 색맹 모드 켜고 끄기, 기본 설정으로 되돌리기 (p12) | `SettingsScreen` | `SettingsScreenTest` | ✅ |
| R1-22 | 바뀐 설정을 저장해 다음 실행에 사용 (p12) | `storage/PropertiesSettingsRepository` | `PropertiesSettingsRepositoryTest`, `PersistenceServicesTest` | ✅ |
| R1-23 | 블록을 더 쌓을 수 없으면 게임 종료 (p13) | `PlayerEngine.spawnNext` | `PlayerEngineTest` | ✅ |
| R1-24 | 종료 시 스코어보드, 순위 안이면 이름 입력, 방금 기록 강조 (p13) | `application/EndGameCoordinator`, `ui/screen/NameEntryScreen`, `ScoreboardScreen` | `EndGameCoordinatorTest`, `NameEntryScreenTest`, `ScoreboardScreenTest` | ✅ |
| R1-25 | 스코어보드 처리 후 시작 메뉴로 또는 프로그램 종료 (p13) | `ScoreboardScreen` (Enter 메뉴, Esc 종료) | `ScoreboardScreenTest` | ✅ |
| R1-26 | 점수 높은 순, 상위 10개 이상, 초기화 전까지 유지 (p14) | `application/ScoreboardService`, `storage/BinaryScoreRepository` | `PersistenceServicesTest`, `BinaryScoreRepositoryTest` | ✅ |

### 비기능

| ID | 요구사항 (슬라이드) | 구현·근거 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| N1-01 | Windows 11, Java 21, 1.2GHz·1GB RAM에서 문제없는 성능 (p4, p15) | 힙 상한 256MB(`build.gradle`), 프레임 16ms 루프 | `ResponsivenessTest` (프레임 처리 시간, 입력 반응) | ✅ |
| N1-02 | 산출물(코드, 문서, 논의) 추적 관리 (p4, p16) | GitHub 저장소, `docs/`, PR | - | ✅ |
| N1-03 | 이슈와 해결 상황 기록 (p16) | GitHub Issues | - | ⚠️ 이슈 미등록 ([프로젝트 관리 규칙](project-management.md) 참고) |
| N1-04 | 팀원별 업무 분담 기록 (p16) | Trello, [백로그 진행 현황](requirements2-backlog.md), [ADR 역할 분담](architecture-decision.md) | - | ✅ |
| N1-05 | 단위 테스트, 라인 커버리지 50~70% 이상 (p17) | JUnit 5, JaCoCo | 전체 403개 | ✅ 92.6% |
| N1-06 | 비기능 요구사항 검증 테스트 1개 이상 (p17) | `test/performance` | `ResponsivenessTest` | ✅ |
| N1-07 | 변경 시마다 테스트를 쉽게 실행하는 체계 (p17) | `gradlew build`, GitHub Actions(`.github/workflows/ci.yml`) | - | ✅ |
| N1-08 | 배포 패키지를 쉽게 만드는 체계, 아이콘 포함 실행 파일, 명령어 없이 실행 (p18) | `gradlew packageZip` (jpackage, JRE 포함 `SETetris.exe`) | - | ✅ v1.0.0 / 🔄 2차 반영 배포는 카드 13 |

## 2. 2차 요구사항

### 기존 방식 변경

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R2-01 | 설정에서 easy·normal·hard 선택 (p3) | `application/model/Difficulty`, `SettingsScreen` | `SettingsScreenTest` | ✅ |
| R2-02 | easy는 I블록 20% 더, hard는 20% 덜 등장 (p3, p4) | `application/PieceWeightPolicy` (I 12/10/8, 나머지 10) | `PieceWeightPolicyTest`, `AppCompositionTest` | ✅ |
| R2-03 | 줄 삭제에 따른 속도 증가가 easy 20% 덜, hard 20% 더 (p3) | `SpeedPolicy(Difficulty)` (레벨당 80/100/120ms) | `SpeedPolicyTest` | ✅ |
| R2-04 | 난이도별 추가 규칙·점수 자유 (p3) | 추가 점수 배율 없음으로 결정 | - | ✅ |
| R2-05 | 스코어보드에서 난이도 구분 (p3) | `ScoreboardService.list(mode, difficulty)`, `ScoreboardScreen` (←→) | `ScoreboardScreenTest`, `PersistenceServicesTest` | ✅ |
| R2-06 | 줄 삭제 애니메이션 (p3) | 지운 줄 내용 보고(`core/result/ClearedRow`)만 준비됨 | - | ❌ 카드 12 |

### 확률에 따른 블록 생성

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R2-07 | 룰렛 휠 선택으로 가중치 확률 생성 (p4) | `core/rule/WeightedRandomGenerator` | `WeightedRandomGeneratorTest` | ✅ |
| R2-08 | 1,000회 이상 반복, ±5% 이내 분포 단위 테스트 (p5) | 요구사항 예시(40/20/20/20) 1,000회, 100,000회 | `WeightedRandomGeneratorTest` | ✅ |
| R2-09 | 실제 게임의 블록 생성 코드가 테스트 통과 (p5) | `AppComposition.generatorFor(Difficulty)` | `AppCompositionTest` (세 난이도 1,000회·100,000회) | ✅ |

### 아이템 모드

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R2-10 | 시작 메뉴에 아이템 모드 추가, 모드에 따라 게임 형태 변경 (p6) | `ScreenRouter.startItemGame()`, `AppComposition.newGame(GameMode)` | `ScreenRouterTest`, `MenuScreenTest`, `AppCompositionTest` | ✅ |
| R2-11 | 스코어보드에서 일반·아이템 모드 구분 (p6) | `ScoreEntry.mode`, `ScoreboardScreen` (↑↓) | `ScoreboardScreenTest`, `BinaryScoreRepositoryTest` | ✅ |
| R2-12 | 10줄 삭제마다 아이템, 미리보기에 새로 나타나는 블록은 아이템 (p7) | `core/rule/ItemPieceSource` | `ItemPieceSourceTest`, `AppCompositionTest` | ✅ |
| R2-13 | 블록 일부가 아이템인 경우와 블록 자체가 아이템인 경우 (p7) | `core/item/AttachedItem`, `core/item/SoloBlock` | `SoloBlockTest` | ✅ |
| R2-14 | 아이템은 특징적인 모양·다른 문자, 아이템마다 다른 문자 (p7) | `BlockGlyphs.of(Item)`, `ColorPalette.itemStyle` | `ItemCatalogTest`, `TextRendererTest` | ✅ |
| R2-15 | 줄 삭제 아이템 `L` (p8) | 엔진의 줄 지정 삭제(`ItemContext.clearRow`)만 준비됨 | - | ❌ 카드 7 |
| R2-16 | 무게추 아이템 (p9) | 1칸 이상 블록 모양(`Shape`) 확장은 준비됨. 접촉 후 좌우 이동 금지·낙하 삭제는 미구현 | - | ❌ 카드 8 |
| R2-17 | 팀 구상 아이템 3개 이상 (p7) | `ChainClearItem`(`C`), `BombItem`(`B`), `DoubleScoreItem`(`$`) | `ChainClearItemTest`, `BombItemTest`, `DoubleScoreTest`, `TeamItemsInEngineTest` | 🔄 카드 9~11 (PR 전) |
| R2-18 | 아이템을 추가하기 쉬운 구조 (p10) | `Item` 구현 + `ItemCatalog.all()`에 한 줄 | `ItemCatalogTest` | ✅ |
| R2-19 | 1차 요구사항과 어긋나지 않는 규칙: 아이템 표시 + 색맹 모드, 난이도 설정 저장, 실행 환경 (p10) | 아이템은 문자로 구분, 설정 파일 v2에 난이도 저장 | `ColorPaletteTest`, `PropertiesSettingsRepositoryTest` | ✅ |
| R2-20 | 팀 아이템 형태·기능 명세서를 평가팀에 제공 (p10) | [아이템 명세서](item-specification.md) | - | 🔄 `L`·무게추 구현 후 확정 |

### 프로젝트 관리와 검증

| ID | 요구사항 (슬라이드) | 구현·근거 | 상태 |
| --- | --- | --- | --- |
| N2-01 | Trello 등으로 진척 상황·할 일 배정 관리, 팀원 생산성 확인 (p11) | Trello 보드, [백로그 진행 현황](requirements2-backlog.md) | ✅ |
| N2-02 | Git·GitHub 버전 관리, 브랜치·PR 정책 (p11) | [프로젝트 관리 규칙](project-management.md), PR #1~#7 | ⚠️ PR 리뷰 기록 없음 |
| N2-03 | 이슈를 GitHub Issue로 등록하고 해결 내역 추적 (p11) | GitHub Issues | ⚠️ 이슈 미등록 |
| N2-04 | 중간평가 라인 커버리지 50%, 기말 70% (p12) | JaCoCo | ✅ 92.6% |

## 3. 남은 작업

| 항목 | 관련 ID | 카드 |
| --- | --- | --- |
| 줄 삭제 아이템 `L` | R2-15 | 7 |
| 무게추 아이템 | R2-16 | 8 |
| 팀 아이템 PR 리뷰·머지, 팀 확정 | R2-17 | 9~11 |
| 줄 삭제 애니메이션 | R2-06 | 12 |
| 2차 반영 배포 패키지 v2.0.0 | N1-08 | 13 |
| 아이템 명세서 확정 | R2-20 | 11 |
| GitHub Issue 등록, PR에 `Closes #번호`와 상대방 리뷰 | N1-03, N2-02, N2-03 | 운영 |

## 4. 해석이 필요한 항목

e-class 답변 전까지 아래처럼 구현했습니다. 답변을 받으면 결과를 [미확정 사항](open-questions.md)과 이 표에 기록합니다.

| 질문 | 현재 구현 |
| --- | --- |
| 난이도를 아이템 모드에도 적용하는가? | 적용. 스코어보드는 모드 × 난이도로 구분 |
| `L` 등 아이템으로 지운 줄도 10줄 카운트에 포함하는가? | 포함 |
| 아이템 카운트는 게임 전체 누적인가, 아이템이 나온 뒤 초기화되는가? | 게임 전체 누적 |
