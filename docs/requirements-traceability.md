# 요구사항 추적표

1·2·3차 요구사항(`TeamProject_Req1.pdf`, `TeamProject_Req2.pdf`, `TeamProject_Req3.pdf`)의 항목별로 구현 위치, 검증 테스트, 상태를 정리한 표입니다. 평가 전에 빠진 항목이 없는지 확인하는 체크리스트로 씁니다. 요구사항마다 거치는 파일과 메서드 흐름은 [요구사항별 코드 흐름](requirements-code-map.md)에 있습니다. 구현이 바뀌면 이 표도 함께 고칩니다.

- 상태: ✅ 충족 / 🔄 진행 중 / ❌ 미구현 / ⚠️ 보완 필요
- 경로는 `src/main/java/team/tetris/` 기준, 테스트는 `src/test/java/team/tetris/` 아래 클래스 이름입니다.
- 마지막 확인: 2026-11-22, `release/v3.0.0` (`main` PR #66까지 + 카드 21·27 + 버전 3.0.0). 1·2차는 2026-10-23에 원문 PDF(1차 19쪽, 2차 12쪽)와 문장 단위로 대조한 뒤 3차 개발 후 다시 확인했고, 3차는 원문 PDF(14쪽)와 [3차 분석](requirements3-analysis.md)의 ID로 대조
- 검증 수치: Windows 11·Java 21에서 `clean build jacocoTestReport packageZip` 성공, 테스트 880개 통과, 라인 커버리지 96.19% (5,551 / 5,771줄). `SETetris-3.0.0-windows.zip`을 풀어 `SETetris.exe` 실행 확인 (v2.0.0 때는 575개, 94.8%)

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
| R1-11 | 반복 키 입력이 무시되지 않고 즉시 반응 (p8) | `ui/input/RepeatController`, `ui/screen/GameScreen` (삭제 연출 중 이동·회전·드롭·키 반복을 보관하고 종료 후 순서대로 적용) | `RepeatControllerTest`, `ResponsivenessTest`, `GameScreenTest`, `AnimationInputTest` | ✅ |
| R1-12 | 일시정지·재개 키 (p8) | `GameScreen` (P 키, Esc 게임 메뉴) | `GameScreenTest` | ✅ |
| R1-13 | 진행·일시정지 중 게임 종료 방법 (OS 기능 제외) (p8) | `GameScreen` 게임 메뉴 "메뉴로 / 프로그램 종료" | `GameScreenTest` | ✅ |
| R1-14 | 블록마다 다른 색, 비슷한·좌우 반전 블록은 대비되는 색·무늬 (p9) | `ui/render/palette/ColorPalette`, `BlockGlyphs`. 화면에는 블록별 무늬 타일(`BlockTile`, `TilePainter`)로 그림. S/Z(사선 / \), J/L(◀ ▶)은 거울상 무늬 | `ColorPaletteTest`, `TextFramePanelTest` | ✅ |
| R1-15 | 색맹 모드: 적록·청황 색맹도 블록 구분 (p10) | `ColorPalette` (색맹용 Okabe-Ito 팔레트 + 블록별 무늬 타일). 무늬는 색과 무관하게 7종이 모두 달라 어떤 색각 이상에서도 구분 | `ColorPaletteTest`, `SettingsScreenTest`, `TextFramePanelTest` (같은 색에서도 무늬가 모두 다른지 검사) | ✅ |
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
| N1-01 | Windows 11, Java 21, 1.2GHz·1GB RAM·500MB 여유 공간에서 문제없는 성능 (p4, p15) | 힙 상한 256MB(`build.gradle`), 프레임 16ms 루프. 배포 실행 폴더 71.6MB, 실행 메모리 약 122MB (2026-10-22 측정) | `ResponsivenessTest` (프레임 처리 시간, 입력 반응) | ✅ (최소 사양 PC 실기 플레이는 미확인) |
| N1-02 | 산출물(코드, 문서, 논의) 추적 관리 (p4, p16) | GitHub 저장소, `docs/`, PR, [프로젝트 진행 기록](project-history.md) | - | ✅ |
| N1-03 | 이슈와 해결 상황 기록 (p16) | GitHub Issues: 카드 #9~#22·#26, 버그 #27~#35 (`bug` 라벨, 고친 커밋·PR 연결) | - | ✅ |
| N1-04 | 팀원별 업무 분담 기록 (p16) | 이슈 담당자, GitHub Projects 보드, [백로그 진행 현황](requirements2-backlog.md), [ADR 역할 분담](architecture-decision.md) | - | ✅ |
| N1-05 | 단위 테스트, 라인 커버리지 50~70% 이상 (p17) | JUnit 5, JaCoCo. 70% 미만이면 build 실패 (`jacocoTestCoverageVerification`) | 전체 880개 | ✅ 96.19% |
| N1-06 | 비기능 요구사항 검증 테스트 1개 이상 (p17) | `test/performance` | `ResponsivenessTest` | ✅ |
| N1-07 | 변경 시마다 테스트를 쉽게 실행하는 체계 (p17) | `gradlew build`(테스트·커버리지 검사 포함), GitHub Actions(`.github/workflows/ci.yml`) | - | ✅ |
| N1-08 | 배포 패키지를 쉽게 만드는 체계, 아이콘 포함 실행 파일, 명령어 없이 실행 (p18) | `gradlew packageZip` (jpackage, JRE 포함 `SETetris.exe`). 2026-10-23 v2.0.0, 2026-11-01 v2.1.0, 2026-11-22 v3.0.0 생성·실행 확인 | - | ✅ v1.0.0, v2.0.0, v2.1.0, v3.0.0 (GitHub Releases) |

## 2. 2차 요구사항

### 기존 방식 변경

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R2-01 | 설정에서 easy·normal·hard 선택 (p3) | `application/model/Difficulty`, `SettingsScreen` | `SettingsScreenTest` | ✅ |
| R2-02 | easy는 I블록 20% 더, hard는 20% 덜 등장 (p3, p4) | `application/PieceWeightPolicy` (I 12/10/8, 나머지 10). p4 예시 "다른 블럭 가중치 10일 때 I형 12"와 같은 방식 | `PieceWeightPolicyTest`, `AppCompositionTest` | ✅ |
| R2-03 | 줄 삭제에 따른 속도 증가가 easy 20% 덜, hard 20% 더 (p3) | `SpeedPolicy(Difficulty)` (레벨당 80/100/120ms) | `SpeedPolicyTest` | ✅ |
| R2-04 | 난이도별 추가 규칙·점수 자유 (p3) | 추가 점수 배율 없음으로 결정 | - | ✅ |
| R2-05 | 스코어보드에서 난이도 구분 (p3) | `ScoreboardService.list(mode, difficulty)`, `ScoreboardScreen` (←→) | `ScoreboardScreenTest`, `PersistenceServicesTest` | ✅ |
| R2-06 | 줄 삭제 애니메이션 (p3) | `SinglePlayerSession`, `LineClearAnimation`, `TextRenderer`: 60ms 흰색 강조·240ms 중앙부터 양쪽으로 삭제 후 압축 표시 | `LineClearAnimationTest`, `LineClearRenderingTest`, `GameScreenTest`, `AnimationInputTest` | ✅ PR #25 (#11) |

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
| R2-14 | 아이템은 특징적인 모양·다른 문자, 아이템마다 다른 문자 (p7) | `BlockGlyphs.of(Item)`, `ColorPalette.itemStyle`. `L`·`C`·`B`·`$`는 반전색 글자, 무게추는 전용 모양과 `@@` 무늬 (`@`는 무게추만 사용) | `ItemCatalogTest`, `TextRendererTest` | ✅ |
| R2-15 | 줄 삭제 아이템 `L` (p8) | `LineClearItem` + `AttachedItem`, 줄 일괄 삭제·점수·누적 줄 수 반영 | `RequiredItemsTest`, `RequiredItemScoringTest` | ✅ PR #24 (#9) |
| R2-16 | 무게추 아이템 (p9) | `WeightBlock`, `PlayerEngine` 하강 시 충돌 후 잠금·낙하 삭제, 화면 `@`, 세션 하강 점수 제외 | `RequiredItemsTest`, `RequiredItemScoringTest`, `TextRendererTest` | ✅ PR #24 (#10), 잠금 시점은 PR #25에서 하강 충돌 기준으로 변경 |
| R2-17 | 팀 구상 아이템 3개 이상 (p7) | `ChainClearItem`(`C`), `BombItem`(`B`), `DoubleScoreItem`(`$`) | `ChainClearItemTest`, `BombItemTest`, `DoubleScoreTest`, `TeamItemsInEngineTest` | ✅ PR #8 (카드 9~11), 2026-10-22 팀 명세 확정 |
| R2-18 | 아이템을 추가하기 쉬운 구조 (p10) | `Item` 구현 + `ItemCatalog.entries()`에 이름과 생성 방식 | `ItemCatalogTest` | ✅ |
| R2-19 | 1차 요구사항과 어긋나지 않는 규칙: 아이템 표시 + 색맹 모드, 난이도 설정 저장, 실행 환경 (p10) | 아이템은 문자로 구분, 설정 파일 v2에 난이도 저장 | `ColorPaletteTest`, `PropertiesSettingsRepositoryTest` | ✅ |
| R2-20 | 팀 아이템 형태·기능 명세서를 평가팀에 제공 (p10) | [아이템 명세서](item-specification.md) | - | ✅ 5종 팀 확정, `v2.0.0` Release에 명세서 링크. 평가 때 화면·인쇄본으로 제공 |

### 프로젝트 관리와 검증

| ID | 요구사항 (슬라이드) | 구현·근거 | 상태 |
| --- | --- | --- | --- |
| N2-01 | Trello 등으로 진척 상황·할 일 배정 관리, 팀원 생산성 확인 (p11) | GitHub Projects 보드, 이슈 담당자, [백로그 진행 현황](requirements2-backlog.md) | ✅ (평가 전 보드의 담당자·상태 최신화) |
| N2-02 | Git·GitHub 버전 관리, 브랜치·PR 정책 (p11) | [프로젝트 관리 규칙](project-management.md), PR #1~#8, #23~#25 | ⚠️ PR #1~#8은 GitHub 리뷰 기록 없음. #23~#25부터 상대방 Review(Approve / Request changes → 수정 → Approve) 기록 |
| N2-03 | 이슈를 GitHub Issue로 등록하고 해결 내역 추적 (p11) | GitHub Issues: 카드 #9~#22·#26 (PR 본문의 `Closes #번호`로 머지 시 자동 종료), 버그 #27~#35 (발견·원인·해결 커밋 기록) | ✅ |
| N2-04 | 중간평가 라인 커버리지 50%, 기말 70% (p12) | JaCoCo, 70% 미만이면 build·CI 실패 | ✅ 96.19% (v3.0.0) |

## 3. 3차 요구사항

3차 요구사항(`TeamProject_Req3.pdf`, 14쪽)의 ID는 [3차 요구사항 분석](requirements3-analysis.md) 1장·2장을 따릅니다. 팀이 정한 해석(안건 ⑩~㉔)은 [미확정 사항](open-questions.md)의 "3차 요구사항 팀 논의 안건"에 있습니다. P2P 항목의 요구사항별 테스트 목록은 [P2P 통신 계약](network-protocol.md) 10장에 더 자세히 있습니다.

### 로컬 대전 모드

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R3-01 | 시작 메뉴에서 대전 모드 선택 (p3) | `ScreenRouter.menuItems()` "대전 모드", `ui/screen/BattleScreen` | `MenuScreenTest`, `BattleScreenTest.theStartMenuLeadsToTheModeChoiceAndIntoABattle` | ✅ PR #60 (#46) |
| R3-02 | 시작할 때 일반 / 아이템 / 시간제한 선택 (p3) | `BattleScreen` 모드 선택, `application/battle/LocalBattleSession` | `BattleScreenTest.theStartMenuLeadsToTheModeChoiceAndIntoABattle`, `theTimeLimitChoiceStartsAtTwoMinutesAndEscapeGoesBackStepByStep` | ✅ PR #60, #63 |
| R3-03 | 일반: 각자 진행하고 지운 줄로 상대 공격 (p3) | `LocalBattleSession`(플레이어별 `SinglePlayerSession` 두 개), `core/rule/AttackRule`, `AttackQueue` | `LocalBattleSessionTest.clearingTwoLinesQueuesTheAttackUntilTheOpponentLocksItsNextPiece`, `BattleScreenTest.eachPlayersKeysMoveOnlyTheirOwnPiece` | ✅ PR #59 (#44) |
| R3-04 | 아이템: 아이템이 나오는 것 외에는 일반과 같음 (p3) | 플레이어별 `ItemPieceSource`, 공유 `SharedPieceSequence` ([아이템 명세서](item-specification.md) 6장) | `ItemBattleTest`, `ItemAttackTest`, `P2pItemAttackTest` | ✅ PR #68 (#48) |
| R3-05 | 시간제한: 시간이 지나면 점수가 높은 쪽 승리 (p3) | `application/battle/BattleTimer`, 1·2·3분 선택(기본 2분, ⑪), 동점 무승부(⑫) | `LocalBattleSessionTest.inputsCountUntilTheExactMomentTheTimeRunsOutAndNotAfter`, `equalScoresWhenTheTimeRunsOutAreADraw`, `aLossInTheSameFrameAsTheTimeRunningOutWins`, `BattleScreenTest.aTimedBattleShowsTheClockAndEndsByScoreAndARematchKeepsTheLimit` | ✅ PR #63 (#47) |
| R3-06 | 2줄 이상 지우면 공격, 마지막 블록 칸을 뺀 모양 (p4) | `AttackRule.linesFor` | `AttackRuleTest.anLPieceClearingThreeLinesSendsThoseLinesWithoutItsOwnCells`, `clearingASingleLineDoesNotAttack`, `theRowOrderOfTheClearIsKept` | ✅ PR #58 (#43) |
| R3-07 | 넘어간 줄을 상대의 받을 줄 영역에 먼저 표시 (p4) | `ui/render/BattleRenderer` 받을 줄 영역 | `BattleRendererTest.waitingAttackLinesAreShownBottomAlignedInTheReceiversArea`, `BattleScreenTest.anAttackIsShownInTheReceiversAreaAndThePlayersInputWaitsForItsOwnClearAnimation` | ✅ PR #60 |
| R3-08 | 상대가 현재 블록을 처리한 뒤, 다음 블록 전에 보드에 추가 (p4) | `core/PlayerEngine` 고정 → 아이템·줄 삭제 → 공격 줄 추가 → 다음 블록, `core/rule/IncomingLines` | `PlayerEngineAttackLinesTest.attackLinesArriveAfterTheClearAndBeforeTheNextPiece`, `attackLinesAreTakenOnlyWhenAPieceLocks`, `LocalBattleSessionTest.attacksArrivingDuringTheReceiversClearAnimationWaitForItsNextLock` | ✅ PR #57 (#42) |
| R3-09 | 상대 보드 아래쪽에 모양 그대로 추가 (p5) | `core/Board` 아래 줄 추가, `core/result/PushResult` | `PlayerEngineAttackLinesTest`, `ItemAttackTest.aLineClearOnARowHoldingOnlyThePieceSendsAnEmptyLine` | ✅ PR #57 |
| R3-10 | 추가된 줄은 회색으로 구분 (p5) | `core/AttackBlock`, `ColorPalette` 회색 + 전용 무늬(색맹 모드 대비, H3-02) | `BattleRendererTest.attackCellsOnTheBoardAreGreyWithTheirOwnPattern` | ✅ PR #57, #60 |
| R3-11 | 한쪽이 더 쌓을 수 없으면 종료, 다른 쪽 승리 (p5) | `LocalBattleSession` 논리적 패배 판정, 밀려 넘치면 패배(⑩), 같은 프레임 동시 패배는 무승부(⑬) | `LocalBattleSessionTest.attackLinesPushingABlockAboveTheBoardLoseTheGame`, `bothPlayersToppingOutInTheSameFrameIsADraw`, `onlyThePlayerWhoToppedOutInTheFrameLoses`, `PlayerEngineAttackLinesTest.cellsPushedAboveTheBoardEndTheGameWithoutSpawning` | ✅ PR #59 |
| R3-12 | 승자 표시, 이후는 일반 게임 종료처럼 처리 (p5) | `BattleScreen` 결과 화면(다시 대전 / 메뉴로) | `BattleScreenTest.toppingOutShowsTheWinnerAndARematchStartsAFreshBattle`, `theResultTitlesCoverEveryEnding` | ✅ PR #60 |
| R3-13 | 대전은 스코어보드에 기록하지 않음 (p5) | `LocalBattleSession`, `P2pMatch`는 기록 서비스를 쓰지 않음 | `BattleScreenTest.forfeitingFromThePauseMenuGivesTheOtherPlayerTheWinAndRecordsNothing`, `P2pMatchSocketTest.realCompositionTwoGamesStatePauseResultAndNoScoreFile` | ✅ |
| R3-14 | 넘길 수 있는 최대 줄 수 10줄 (p6) | `core/rule/AttackQueue` | `AttackQueueTest.attacksThatAddUpToExactlyTenLinesAreKeptWhole`, `aSingleAttackLargerThanTheQueueKeepsItsTopTenLines` | ✅ PR #58 |
| R3-15 | 여러 번 공격하면 기존 줄의 아래쪽에 추가 (p6) | `AttackQueue.add` | `AttackQueueTest.newAttacksGoBelowTheWaitingLinesAndTakeEmptiesTheQueue` | ✅ PR #58 |
| R3-16 | 이미 10줄이면 새 공격은 무시 (p6) | `AttackQueue.add` | `AttackQueueTest.aFullQueueIgnoresNewAttacks` | ✅ PR #58 |
| R3-17 | 합쳐서 10줄이 넘으면 아래쪽을 잘라냄 (p6) | `AttackQueue.add` | `AttackQueueTest.eightWaitingLinesPlusThreeKeepTheEightAndTheTopTwoOfTheNewAttack`, `concurrentAttacksNeverExceedTheLimitOrLoseAcceptedLines` | ✅ PR #58 |
| R3-18 | 아이템 사용 규칙은 팀이 정함 (p5) | 안건 ⑭·⑮·⑰·㉔, [아이템 명세서](item-specification.md) 6장 `IT-BAT-01~09` | `ItemAttackTest`, `ItemBattleTest`, `AttackRuleTest` | ✅ PR #68 |

### P2P 대전 모드

| ID | 요구사항 (슬라이드) | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| R3-20 | 시작 메뉴에서 P2P 대전, 규칙은 로컬 대전과 같음 (p8) | `ScreenRouter.menuItems()` "P2P 대전", `application/network/P2pMatch` (같은 `AttackRule`·`AttackQueue` 사용) | `MenuScreenTest`, `P2pMatchTest.bothScreensAttackTopOutAndSecondMatch`, `P2pItemAttackTest` | ✅ PR #64 (#51) |
| R3-21 | 서버 또는 클라이언트 선택, 클라이언트가 서버에 접속 (p8) | `ui/screen/P2pLobbyScreen`, `P2pLobby`, `network/SocketMatchTransport` (TCP 21313) | `SocketMatchTransportTest`, `P2pLobbySocketTest.actualSocketConnectConfigureStartAndRematch` | ✅ PR #61 (#49), #62 (#50) |
| R3-22 | 서버가 접속용 IP를 화면에 표시하고 대기 (p8) | `network/LocalIpv4Addresses`, `P2pLobbyScreen` (활성 IPv4 전부, 5개 넘으면 페이지) | `P2pLobbyScreenTest.menuServerAddressPagingAndWindowClose` | ✅ PR #62 |
| R3-23 | 클라이언트가 IP를 입력해 접속 (p8) | `application/network/Ipv4Address`, `P2pLobbyScreen` | `P2pLobbyScreenTest.clientInputValidationConnectionAndServerOnlyConfiguration`, `FileRecentIpRepositoryTest.invalidIpIsRejectedWithoutDns` | ✅ PR #62 |
| R3-24 | 연결 후 양쪽에 알리고, 서버가 모드 선택 (p8) | `P2pLobby.configure` (모드·시간·난이도) | `P2pLobbyScreenTest.serverControlsDefaultTimeAndReadyResetAndStartedDisplay`, `P2pLobbyTest.difficultyChangeReachesClientAndSurvivesModeChange` | ✅ PR #62 |
| R3-25 | 양쪽이 모두 '게임 시작'을 눌러야 시작 (p8) | `P2pLobby.ready`, 설정이 바뀌면 양쪽 준비 해제 | `P2pLobbyTest.oneReadyPlayerNeverStartsAndBothStartExactlyOnce`, `configurationChangesClearBothReadyFlagsAndStaleReadyCannotStart` | ✅ PR #62 |
| R3-26 | 내 화면과 상대 화면이 모두 보이고 실시간 반영 (p9) | `PlayerState`·`PlayerStates`(보드 상태 전송), `ui/render/P2pRenderer`, `ui/screen/P2pMatchScreen` | `P2pMatchTest.bothScreensAttackTopOutAndSecondMatch`, `P2pMatchScreenTest.routersRenderTwoBoardsResultReturnAndSecondGame`, `PlayerStatesTest` | ✅ PR #64 |
| R3-27 | 끝나면 승패 표시 후 연결 직후 대기 상태로, 다시 시작 가능 (p9) | `P2pMatch` 결과 → `P2pLobby` 재대전 | `P2pLobbyTest.rematchResetsReadinessAndPreservesConnectionSequenceAndMatchIds`, `P2pMatchSocketTest.realCompositionTwoGamesStatePauseResultAndNoScoreFile` | ✅ PR #64 |
| R3-28 | 전송이 느려지면 랙 표시 (p10) | `network/PingTracker`(RTT 200ms 초과), `MatchTransport.lagging()`, 경기 화면 지연 경고 | `PingTrackerTest`, `SocketMatchTransportTest.delayedPongShowsLagAndNextFastPongRecoversOnRealSocket`, `P2pNetworkFailureScreenTest.lagRecoveryAndDisconnectReturnToChoiceInEveryConnectedPhase` | ✅ PR #65 (#52) |
| R3-29 | 일정 시간 이상 끊기면 오류를 표시하고 P2P 첫 화면으로 (p10) | 3초 무응답 시 `FAILED`와 한국어 사유, 2초 뒤 첫 화면 | `P2pRecoverySocketTest`, `P2pNetworkFailureScreenTest.peerDisconnectShowsOutcomeReasonAndDestination`, `SocketMatchTransportTest.unresponsiveEstablishedPeerTimesOutCleansWorkersAndReconnectsOnSamePort` | ✅ PR #65, #66 (#53) |
| R3-30 | 지연·끊김 기준을 팀이 정하고 관리 (p10) | `application/network/NetworkPolicy` (랙 200ms, 끊김 3초, 안건 ㉑), [P2P 통신 계약](network-protocol.md) 9장 | `PingTrackerTest` | ✅ PR #65 |
| R3-31 | 최근 접속 IP 저장, 클라이언트가 골라 입력 (p10) | `RecentIps`, `storage/FileRecentIpRepository` (5개, 원자적 저장, ㉒) | `FileRecentIpRepositoryTest`, `P2pLobbyTest.connectionStagesAndOnlySuccessfulClientConnectionIsRemembered`, `P2pLobbyScreenTest.selectingRecentIpAndHeldEnterDoNotSkipConnectionSteps` | ✅ PR #62 |
| R3-32 | 접속 과정을 알 수 있는 메시지 (p10) | `P2pLobby.status()`, `P2pLobbyScreen` (접속 중·확인 중·연결됨·실패 사유) | `P2pLobbyTest.connectionStagesAndOnlySuccessfulClientConnectionIsRemembered`, `P2pLobbyScreenTest.failureShowsReasonAndRetryOrNavigationDisposesTransport`, `P2pNetworkFailureScreenTest.connectionErrorsAlsoAutoReturnAndCanBeAcknowledgedEarly` | ✅ PR #62, #65 |

### 비기능

| ID | 요구사항 (슬라이드) | 구현·근거 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| N3-01 | 같은 LAN에서 키 입력과 화면 표시 사이 지연 200ms 이하 (p9) | `InputLatency`(입력 → 상대 화면 표시 확인 왕복), `--debug` 로그와 `tools/p2p_latency_report.py`. 측정 절차는 [P2P 통신 계약](network-protocol.md) 8장 | `InputLatencyTest`, 같은 공유기 LAN의 PC 2대 리허설 | ✅ 같은 공유기 망 PC 2대 리허설(2026-11-18)에서 눈에 띄는 지연 없이 동작. 300개 표본 수치는 시연 전 리허설에서 기록 ([P2P 통신 계약](network-protocol.md) 8장) |
| N3-02 | 네트워크 접속 과정 자동화 테스트 (p10) | 실제 localhost 소켓 테스트, CI(Windows·Java 21)에서 실행 | `SocketMatchTransportTest`, `P2pLobbySocketTest`, `P2pMatchSocketTest`, `P2pRecoverySocketTest` | ✅ PR #66 (#53) |
| N3-03 | 평가장 네트워크 저하에 대비한 테스트 (p14) | 가짜 통신·가짜 시계로 지연·무응답·회복 재현, 예비 네트워크(휴대폰 핫스팟) | `PingTrackerTest`, `P2pNetworkFailureScreenTest`, `SocketMatchTransportTest.delayedPongShowsLagAndNextFastPongRecoversOnRealSocket` | ✅ 자동화. 실제 저하 환경은 시연 전 핫스팟으로 확인 ([평가 준비](evaluation-guide.md)) |

### 1·2차 요구사항 관련 변경

| ID | 요구사항 | 구현·근거 | 상태 |
| --- | --- | --- | --- |
| U3-01 | 1·2차 비기능 요구사항 평가 강화, 단위 테스트로 충분히 검증 | 1·2장의 모든 항목을 v3.0.0 기준으로 다시 확인 (표의 클래스·테스트가 모두 존재하고 통과). 반응성 `ResponsivenessTest` | ✅ |
| U3-02 | 기말 라인 커버리지 70% | JaCoCo, 70% 미만이면 build·CI 실패 | ✅ 위 검증 수치 |
| U3-03 | 코드 리뷰, 리팩토링, 버전관리 | 카드마다 이슈·브랜치·PR(`Closes #번호`). 리팩토링은 별도 PR(#57 core 고정 지점, #60 `BoardPainter` 분리). 상대방 Review 기록: #57, #58, #60, #61, #62, #64, #65, #66 (수정 요청 → 반영 → 승인 포함) | ⚠️ #56, #59, #63, #67은 GitHub Review 없이 머지 (대면 확인) |
| U3-04 | 팀 아이템 요구사항 명세서 작성 품질 | [아이템 명세서](item-specification.md)를 요구사항 명세 형식(ID, 우선순위, 수용 기준, 관련 테스트)으로 다시 씀 | ✅ 카드 27 (#54) |
| U3-05 | 1·2·3차 요구사항 모두 평가, 3차 개발 후 재점검 | 이 추적표 (1·2·3장) | ✅ 카드 28 (#55) |

### 파생 요구사항과 설계 고려사항

| ID | 내용 | 구현 | 테스트 | 상태 |
| --- | --- | --- | --- | --- |
| H3-01 | 2P 조작 키, 키 설정·저장, 충돌 방지, 기존 설정 보존 | `application/model/BattleKeyBindings`, 설정 파일 형식 3, 1P W A D S Space / 2P ↑ ← → ↓ Enter (⑲) | `BattleKeyBindingsTest`, `BattleKeysTest`, `SettingsScreenTest`, `PropertiesSettingsRepositoryTest` | ✅ PR #67 (#45) |
| H3-02 | 공격 줄의 색맹 구분 | 공격 칸 전용 무늬 | `BattleRendererTest.attackCellsOnTheBoardAreGreyWithTheirOwnPattern` | ✅ |
| H3-03 | 공격 줄로 밀려 넘칠 때 패배 기준 (⑩) | 보드 밖으로 밀린 칸이 있으면 패배 | `PlayerEngineAttackLinesTest.cellsPushedAboveTheBoardEndTheGameWithoutSpawning`, `attackLinesThatBlockTheSpawnEndTheGameEvenWithoutOverflow` | ✅ |
| H3-04 | 동점·동시 종료 (⑫·⑬·㉓) | 무승부, 같은 프레임 판정, 패배가 시간 만료보다 우선 | `LocalBattleSessionTest.bothPlayersToppingOutInTheSameFrameIsADraw`, `aLossInTheSameFrameAsTheTimeRunningOutWins`, `P2pMatchTest.simultaneousDefeatIsDrawAndTimedTieIsDraw` | ✅ |
| H3-05 | 일시정지와 종료 (⑱) | 양쪽 보드·시계 정지, 정지 중 나가면 패배 | `LocalBattleSessionTest.pausingByEitherPlayerStopsBothBoardsUntilResumed`, `quittingWhilePausedLosesTheGameForTheQuitter`, `P2pMatchTest.pauseOwnerAndServerClockAreAuthoritative` | ✅ |
| H3-06 | 두 명의 동시 입력·키 반복 독립 | 플레이어별 반복 입력 | `BattleScreenTest.holdingKeysAtTheSameTimeRepeatsForBothPlayers` | ✅ |
| H3-07 | 두 보드 화면에서도 화면 크기 3단계 | 격자 크기에 맞춘 창 크기 | `BattleRendererTest.bothBoardsAreDrawnSideBySideOnAWiderGrid` | ✅ |
| H3-08 | Windows 방화벽 | 처음 서버를 열 때 Windows 허용 창에서 허용. 시연 노트북에서 미리 허용 ([평가 준비](evaluation-guide.md)) | PC 2대 리허설 | ✅ |
| H3-09 | 접속 가능한 IP 표시 | 활성 IPv4를 모두 표시, 페이지 이동 | `P2pLobbyScreenTest.menuServerAddressPagingAndWindowClose` | ✅ |
| H3-10 | 통신 호환성 | 접속 직후 HELLO로 프로토콜 버전 교환, 다르면 양쪽 거부 | `SocketMatchTransportTest.handshakeRejectsIncompatibleProtocolsOnBothEnds`, `P2pLobbySocketTest.incompatiblePeerFailsWithoutSavingIp` | ✅ |
| H3-11 | 시간제한 기준 시계 | 서버 시계 기준, 만료 시 양쪽 마감 점수로 판정 | `P2pMatchTest.pauseOwnerAndServerClockAreAuthoritative`, `exactTimeoutUsesScoresAndFreezesFurtherInput`, `clientStopsAtLocalTimeLimitEvenWhenServerNoticeIsDelayed` | ✅ |
| H3-12 | 양쪽 블록 순서 (⑰) | 기본 블록은 같은 순서, 아이템은 각자 | `SharedPieceSequenceTest`, `ItemBattleTest.itemsFollowEachPlayersOwnClearsWhileThePlainPiecesStayShared` | ✅ |
| H3-13 | 최근 IP 저장 실패에도 실행 | 파일이 없거나 깨져도 빈 목록으로 계속, 원자적 저장 | `FileRecentIpRepositoryTest.corruptFileFallsBackWithoutOverwriting`, `atomicCommitFailurePreservesPreviousFileAndAllowsSessionToContinue` | ✅ |
| H3-14 | 대전 종료 후 흐름 | 로컬은 다시 대전 / 메뉴로, P2P는 로비로 | `BattleScreenTest.toppingOutShowsTheWinnerAndARematchStartsAFreshBattle`, `P2pMatchScreenTest.routersRenderTwoBoardsResultReturnAndSecondGame` | ✅ |

도전 과제(CH-1~3)는 요구사항 p12에 따라 `main`에 머지하지 않으므로 이 표에 넣지 않습니다.

## 4. 남은 작업

| 항목 | 관련 ID | 카드 |
| --- | --- | --- |
| 앞으로의 PR에 `Closes #번호`와 상대방 Review 기록 | N2-02, U3-03 | 운영 |
| 평가 전 GitHub Projects 보드의 담당자·상태 최신화 | N2-01 | 운영 |
| 시연 전날 시연 노트북 두 대로 배포 zip 실행, 방화벽 허용, 핫스팟 예비 네트워크 확인 | N3-03, H3-08 | 운영 |

## 5. 해석이 필요한 항목

요구사항 원문에서 해석이 갈리는 부분은 팀 내부 논의로 정합니다. 안건별 원문, 구현, 결정과 근거는 [미확정 사항](open-questions.md)의 "2차 요구사항 팀 논의 안건"에 기록합니다.

| 질문 | 현재 구현 | 상태 |
| --- | --- | --- |
| 줄 삭제 연출 중 누른 키를 어떻게 처리하는가? (1차 p8 "반복된 키 입력이 무시되지 않고") | 보관했다가 연출 후 순서대로 처리 | 10-22 결정 |
| 무게추는 언제 "닿았다"고 보는가? | 하강을 시도하다 막힌 순간 좌우 잠금 | 10-22 결정 |
| 난이도를 아이템 모드에도 적용하는가? (원문: "기존의 일반 게임 모드에 난이도 설정을 추가") | 적용. 스코어보드는 모드 × 난이도로 구분 | 10-23 결정 |
| `L` 등 아이템으로 지운 줄도 10줄 카운트에 포함하는가? | 포함 | 10-23 결정 |
| 아이템 카운트는 게임 전체 누적인가, 아이템이 나온 뒤 초기화되는가? | 게임 전체 누적 | 10-23 결정 |
| 무게추가 고정되며 꽉 찬 줄에 점수를 주는가? (원문: "지워지는 블럭 등에 대해서는 점수를 부여하지 않습니다") | 무게추가 지운 칸·하강 거리는 0점, 꽉 찬 줄은 일반 줄 삭제 점수 | 10-23 결정 |
| 다음 블록 미리보기는 몇 개인가? (원문: "다음에 생성될 블럭들을 표시") | 1개 (원문 예시 화면과 같음) | 10-23 결정 |
| 난이도별 속도 증가 ±20%의 기준은? | 레벨당 낙하 간격 감소량 80/100/120ms, 최소 100ms 공통 | 10-23 결정 |
