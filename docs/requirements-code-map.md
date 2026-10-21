# 요구사항별 코드 흐름

요구사항 하나가 실제로 어떤 파일과 메서드를 거쳐 동작하는지 정리한 문서입니다. 평가나 리뷰에서 "이 요구사항은 코드 어디에 있나"를 바로 찾아 보여 줄 때 씁니다. 항목 ID는 [요구사항 추적표](requirements-traceability.md)와 같습니다.

- 경로는 `src/main/java/team/tetris/` 기준입니다.
- `A → B`는 A가 B를 호출한다는 뜻입니다.
- 마지막 확인: 2026-10-13, `feat/team-items` 브랜치

## 0. 공통 뼈대

모든 요구사항이 아래 흐름 위에서 동작합니다.

### 실행과 조립

```
bootstrap/TetrisApplication.main
  → ServiceLoader로 ui/TetrisUi 찾기 (META-INF/services/team.tetris.application.port.GameUi)
  → new bootstrap/AppComposition(데이터 폴더)
      설정 저장소, 기록 저장소, 종료 조율자 생성 (게임은 아직 만들지 않음)
  → TetrisUi.open(AppComposition)
      → createWindow: JFrame + ui/render/TextFramePanel + ui/ScreenRouter + ui/GameLoop
```

### 매 프레임 (약 16ms)

```
ui/GameLoop.tick (Swing Timer)
  → ScreenRouter.update(경과 나노초) → 현재 Screen.update
  → ScreenRouter.render() → 현재 Screen.render → TextFrame(44×24 글자 격자)
  → TextFramePanel.show(frame) → 글자와 색을 창에 그림
```

### 키 입력

```
TetrisUi의 KeyListener.keyPressed
  → ui/input/KeyNames.nameOf(키 코드) → "LEFT" 같은 키 이름
  → ScreenRouter.keyPressed → 현재 Screen.onKeyPressed
```

화면 전환은 모두 `ScreenRouter.show(Screen)`을 거칩니다. 화면이 바뀔 때 눌려 있던 키는 뗄 때까지 무시합니다(`ignoreHeldKeys`).

### 한 판의 진행

```
GameScreen.update(경과 시간)
  → application/SinglePlayerSession.update   중력: 쌓인 시간이 낙하 간격을 넘으면 engine.tick()
  → 큐에 쌓인 명령마다 SinglePlayerSession.handle(GameCommand)
       → core/PlayerEngine.apply(GameAction)
  → SinglePlayerSession.accept(EngineStep)   점수·줄 수·레벨·속도·상태 갱신
```

엔진(`core`)은 점수와 시간을 모르고 "무슨 일이 있었는지"(`core/result/EngineStep`)만 보고합니다. 점수와 속도는 세션(`application`)이 정책 객체로 계산합니다.

---

## 1. 1차 요구사항

### R1-01~04 시작 메뉴

| 단계 | 코드 |
| --- | --- |
| 첫 화면 | `ScreenRouter` 생성자 → `showMenu()` → `ui/screen/MenuScreen` |
| 게임 이름 | `MenuScreen.drawTitle`: "TETRIS"를 블록 문자로 그림 |
| 메뉴 항목 | `ScreenRouter.menuItems()`: 게임 시작, 아이템 모드, 설정, 스코어보드, 종료 |
| 이동과 선택 | `MenuScreen.onKeyPressed`: ↑↓ → `move`, Enter → 선택한 `MenuItem.action().run()` |
| 사용 가능 키 표시 | 다른 키를 누르면 `showKeyHint` → 화면 아래 "사용할 수 있는 키: ↑ ↓ Enter" |
| 메뉴 추가 구조 | `menuItems()`에 `new MenuItem("이름", 동작)` 한 줄 추가 |

테스트: `MenuScreenTest`, `ScreenRouterTest`

### R1-05~08 보드, 블록 생성, 줄 삭제, 화면 구성

```
메뉴 "게임 시작" → ScreenRouter.startGame
  → AppComposition.newGame(GameMode.NORMAL)
       → generatorFor(난이도)  = core/rule/WeightedRandomGenerator(application/PieceWeightPolicy.weightsFor(난이도))
       → core/rule/PlainPieceSource(생성기)
       → core/PlayerEngine(10, 20, 공급자)       ← 10칸 × 20줄
       → application/SinglePlayerSession(엔진, ScorePolicy, SpeedPolicy, 모드, 난이도)
  → ui/screen/GameScreen
```

| 요구사항 | 코드 |
| --- | --- |
| 20×10 보드 | `core/Board`(칸 배열 `Cell[][]`), 크기는 `AppComposition.newGame`의 `PlayerEngine(10, 20, …)` |
| 7종 블록 | `core/TetrominoType`(I, O, T, S, Z, J, L), 회전 상태별 칸 좌표표 |
| 같은 확률 | normal 난이도는 7종 가중치가 모두 10 → `WeightedRandomGenerator.spin` |
| 줄 삭제 | `PlayerEngine.lockActivePieceAndSpawnNext` → `Board.clearRows`: 꽉 찬 줄 삭제 후 위 줄을 내림 |
| 화면 구성 | `ui/render/TextRenderer.render`: `drawBoard`(보드), `drawActivePiece`(떨어지는 블록), `drawNext`(NEXT), `drawStats`(모드·난이도·SCORE·LEVEL·LINES), `drawHelp`(키 안내) |

`GameSnapshot.mode()`·`difficulty()`는 게임 시작 시 확정한 분류를 전달하고, `drawStats`는 NEXT 위에 `일반 / EASY`처럼 표시합니다. 보드 빈 칸은 `BlockGlyphs.EMPTY`의 전각 가운뎃점(`・`)이며, `TextFramePanel`이 실제 글자 영역을 기준으로 칸 중앙에 그립니다.

테스트: `BoardTest`, `PlayerEngineTest`, `TetrominoTypeTest`, `TextRendererTest`, `TextFramePanelTest`

### R1-09 1초 낙하와 가속

```
GameScreen.update → SinglePlayerSession.update(경과 시간)
  accumulatedNanos += 경과 시간
  낙하 간격(gravityIntervalNanos)을 넘을 때마다 → PlayerEngine.tick → descendOrLock (한 칸 아래, 막히면 고정)
줄 삭제 → SinglePlayerSession.accept
  → SpeedPolicy.levelFor(누적 줄 수) = 줄 수 / 10
  → SpeedPolicy.gravityIntervalNanos(레벨) = max(100ms, 1000ms − 레벨 × 감소량)
```

- 레벨 0의 간격이 1000ms → 1초에 1칸
- 렉으로 시간이 몰려도 한 프레임에 최대 5칸까지만 내림(`MAX_TICKS_PER_UPDATE`)

테스트: `SinglePlayerSessionTest`, `SpeedPolicyTest`

### R1-10 이동, 회전, 한 번에 떨어뜨리기

```
GameScreen.onKeyPressed → pressDuringPlay
  → ui/input/InputMapper.commandFor(키, RUNNING) → GameCommand
  → pending 큐에 추가
GameScreen.update → SinglePlayerSession.handle(명령) → GameCommand를 GameAction으로 변환
  → PlayerEngine.apply
       MOVE_LEFT/RIGHT → tryShift: Board.canPlace로 확인 후 ActivePiece.movedTo
       SOFT_DROP       → descendOrLock: 한 칸 아래 (DropResult(1))
       ROTATE_CW       → core/rule/RotationSystem.tryRotate: 제자리 회전 → 막히면 벽차기 후보 8개 순서대로
       HARD_DROP       → hardDrop: 바닥까지 내린 칸 수를 세고 바로 고정
```

테스트: `PlayerEngineTest`, `RotationSystemTest`, `InputMapperTest`

### R1-11 반복 입력 즉시 반응

| 단계 | 코드 |
| --- | --- |
| 첫 입력 | `GameScreen.pressDuringPlay` → 다음 프레임에 바로 처리 |
| OS 자동 반복 무시 | `ui/input/RepeatController.press`: 떼지 않은 키가 다시 들어오면 `false` |
| 직접 만드는 반복 | `RepeatController.due`: 170ms 뒤부터 50ms 간격, `GameScreen.update`에서 큐에 추가 |
| 반복 가능 명령 | `InputMapper.isRepeatable`: 좌우 이동, 아래 이동 |

테스트: `RepeatControllerTest`, `ResponsivenessTest`(다음 프레임 반영, 한 프레임 안의 연타 보존, 누르고 있기)

### R1-12~13 일시정지, 재개, 게임 중 종료

```
P 키 → InputMapper → PAUSE → SinglePlayerSession.handle → PlayerEngine.apply(PAUSE) → EnginePhase.PAUSED
Esc 키 → QUIT_GAME을 PAUSE로 바꿔 일시정지 메뉴 열기 (GameScreen.pressDuringPlay)
일시정지 메뉴 (GameScreen.pressInPauseMenu, drawPauseMenu)
  재개          → RESUME
  메뉴로        → QUIT_GAME → SinglePlayerSession.finish(ABORTED) → ScreenRouter.finishGame → 메뉴
  프로그램 종료 → ScreenRouter.exit
창이 포커스를 잃으면 → GameScreen.onFocusLost → 자동 일시정지
```

테스트: `GameScreenTest`

### R1-14~15 블록 색, 무늬, 색맹 모드

| 요구사항 | 코드 |
| --- | --- |
| 블록별 색 | `ui/render/palette/ColorPalette.standard()`: 7종 각각 다른 색 |
| 비슷한 블록 구분 | `BlockGlyphs.of(Shape)`: S `//` ↔ Z `\\`, J `((` ↔ L `))` 처럼 거울상 무늬 |
| 색맹 모드 | `ColorPalette.colorBlindFriendly()`: 적록·청황 색맹도 구분되는 색 조합 |
| 적용 | `ColorPalette.of(설정의 colorBlindMode)` → `GameScreen`·`TextRenderer` 생성 시 선택 |

테스트: `ColorPaletteTest`

### R1-16~18 점수

```
SinglePlayerSession.accept(EngineStep)
  떨어진 칸 = step.dropResult().cellsDropped()   (tick·소프트 드롭은 1, 하드 드롭은 내린 칸 수)
  지운 줄   = step.clearResult().lineCount()
  → application/ScorePolicy.scoreFor(떨어진 칸, 지운 줄, 동작 직전 레벨)
       = 떨어진 칸 × (레벨 + 1)      ← 1칸마다 점수, 속도가 빨라지면(레벨 ≥ 1) 추가 점수
       + 100 × 지운 줄²              ← 제시되지 않은 추가 방식 (동시 삭제 보너스)
  → GameSnapshot.score → TextRenderer.drawStats (매 프레임 그리므로 실시간 표시)
```

테스트: `ScorePolicyTest`, `SinglePlayerSessionTest`

### R1-19~22 설정 화면과 저장

```
메뉴 "설정" → ScreenRouter.showSettings → ui/screen/SettingsScreen
  ↑↓ 항목 이동, ←→ adjust (값 변경), Enter activate
  값 변경 → save(new Settings(…)) → ScreenRouter.updateSettings
       → application/SettingsService.update
       → storage/PropertiesSettingsRepository.save → AtomicFileRepository.write
            임시 파일에 쓴 뒤 원자적 교체(ATOMIC_MOVE) → settings.properties
       → 저장 성공 시에만 적용 → TetrisUi.fitToScreen (화면 크기 다시 맞춤)
```

| 설정 | 코드 |
| --- | --- |
| 화면 크기 3종 | `Settings.ScreenSize`(SMALL/MEDIUM/LARGE) → `TextFramePanel.fontSizeFor`: 14/18/24pt. 화면보다 크면 `setScreenSize(size, limit)`가 글자를 줄임 |
| 키 변경 | `SettingsScreen.activate`(KEY) → 키 입력 대기 → `bind`: 다른 조작과 겹치면 거부 |
| 스코어보드 초기화 | `runConfirmed`(CLEAR_SCORES) → 확인 후 `ScreenRouter.clearScores` → `ScoreboardService.clear` |
| 색맹 모드 | `adjust`(COLOR_BLIND) |
| 기본값 복원 | `runConfirmed`(RESET) → `SettingsService.reset` |
| 다음 실행에 불러오기 | `TetrisUi.createWindow` → `SettingsService.loadOrDefault` → `PropertiesSettingsRepository.load`. 파일이 깨졌으면 기본값으로 시작하고 메뉴에 안내 |

테스트: `SettingsScreenTest`, `PropertiesSettingsRepositoryTest`, `PersistenceServicesTest`, `TextFramePanelTest`

### R1-23~26 게임 종료와 스코어보드

```
새 블록을 놓을 수 없음 → PlayerEngine.spawnNext → EnginePhase.GAME_OVER
  → SinglePlayerSession.accept → finish → GameResult(점수, 모드, 난이도 등)
  → GameScreen.update: 1.2초 GAME OVER 표시 → ScreenRouter.finishGame
  → application/EndGameCoordinator.begin(결과)
       ScoreboardService.qualifies: 같은 모드·난이도 상위 10위 안인가?
         예  → NAME_REQUIRED → ui/screen/NameEntryScreen
                 Enter → submit → ScreenRouter.submitName → EndGameCoordinator.submitName
                   → ScoreboardService.register (ScoreboardPolicy.normalizeName: 1~12자, 제어 문자 금지)
                   → storage/BinaryScoreRepository.save → scores.bin
                 → SHOW_SCOREBOARD → ScoreboardScreen.afterGame (방금 기록 강조)
         아니오 → SHOW_SCOREBOARD
  → ScoreboardScreen: Enter 메뉴로, Esc 프로그램 종료
```

| 요구사항 | 코드 |
| --- | --- |
| 점수 높은 순 정렬, 상위 10개 | `ScoreboardService.ranked`: 점수 내림차순, 모드·난이도 조합마다 `ScoreboardPolicy.capacity`(10)개 |
| 종료 후에도 유지 | `BinaryScoreRepository`(버전 있는 이진 파일, 원자적 쓰기) |
| 중도 포기한 판 | `ScoreboardPolicy.accepts`: GAME_OVER만 등록 |
| 저장 실패 | `EndGameView`의 오류 → 이름 입력 화면에서 Enter로 다시 시도 |

테스트: `EndGameCoordinatorTest`, `NameEntryScreenTest`, `ScoreboardScreenTest`, `BinaryScoreRepositoryTest`

### N1 비기능 요구사항

| 요구사항 | 코드·설정 |
| --- | --- |
| 성능 | `GameLoop`(16ms), `build.gradle`의 `-Xmx256m`. `test/performance/ResponsivenessTest`: 프레임 처리 시간, 입력 반응 |
| 테스트 체계 | JUnit 5 + JaCoCo(`build.gradle`), GitHub Actions(`.github/workflows/ci.yml`, Windows·Java 21) |
| 배포 | `build.gradle`의 `packageApp`(jpackage, JRE 포함, `src/main/resources/icon.ico`) → `packageZip` |
| 창 아이콘 | `TetrisUi.icons`: `src/main/resources/icon.png` |

---

## 2. 2차 요구사항

### R2-01~05 난이도

```
설정 화면 "난이도" ←→ → SettingsScreen.adjust(DIFFICULTY) → Settings.difficulty 저장 (settings.properties schemaVersion 2)
메뉴 "게임 시작" → AppComposition.newGame
  difficulty = settings.loadOrDefault().value().difficulty()   ← 시작할 때 고정
  → generatorFor(difficulty): PieceWeightPolicy.weightsFor → I블록 12/10/8, 나머지 10
  → speeds.apply(difficulty) = new SpeedPolicy(difficulty): 레벨당 80/100/120ms 감소
  → SinglePlayerSession(…, mode, difficulty) → GameResult에 난이도 기록
스코어보드 → ScoreboardScreen: ←→ changeDifficulty → ScoreboardService.list(mode, difficulty)
```

| 요구사항 | 코드 |
| --- | --- |
| 난이도 종류 | `application/model/Difficulty`(EASY, NORMAL, HARD) |
| I블록 ±20% | `application/PieceWeightPolicy` |
| 속도 증가율 ±20% | `application/SpeedPolicy(Difficulty)` |
| 기존 설정 파일 호환 | `PropertiesSettingsRepository.decode`: 버전 1 파일은 NORMAL로 읽음 |
| 스코어보드 구분 | `ScoreEntry.difficulty`, `BinaryScoreRepository`(버전 2, 버전 1 기록은 일반·NORMAL로 읽음) |

테스트: `PieceWeightPolicyTest`, `SpeedPolicyTest`, `SettingsScreenTest`, `ScoreboardScreenTest`

### R2-07~09 확률에 따른 블록 생성 (룰렛 휠)

```
core/rule/WeightedRandomGenerator(가중치 Map, 난수)
  생성자: 가중치를 누적 합 배열로 만듦  예) I 12 → [12, 22, 32, …, 72]
  spin(): random.nextInt(합) → 그 값이 처음으로 누적 합보다 작아지는 블록
  next(): 미리 뽑아 둔 블록을 내주고 새로 뽑음 / peek(): 미리보기
```

| 요구사항 | 테스트 |
| --- | --- |
| 요구사항 예시(블록 4개, 하나만 2배 → 40/20/20/20) 1,000회 ±5% | `WeightedRandomGeneratorTest` |
| 실제 게임 생성 코드(`AppComposition.generatorFor`)로 세 난이도 1,000회·100,000회 | `AppCompositionTest` |

### R2-10~11 아이템 모드 메뉴와 스코어보드 구분

```
메뉴 "아이템 모드" → ScreenRouter.startItemGame → AppComposition.newGame(GameMode.ITEM)
  → PlainPieceSource를 core/rule/ItemPieceSource로 한 겹 감쌈 (아이템 목록 = core/item/ItemCatalog.all())
  → SinglePlayerSession(…, GameMode.ITEM, …) → GameResult.mode → ScoreEntry.mode
스코어보드 → ScoreboardScreen: ↑↓로 mode 전환 → ScoreboardService.list(mode, difficulty)
```

테스트: `ScreenRouterTest`, `AppCompositionTest`, `ScoreboardScreenTest`

### R2-12 10줄마다 아이템, 미리보기 표시

```
블록 고정 → PlayerEngine.lockActivePieceAndSpawnNext
  → Board.clearRows → 지운 줄이 있으면 source.onLinesCleared(n)
       ItemPieceSource: totalLines += n, 새로 넘은 10의 배수만큼 itemsPending 증가
  → spawnNext → source.next(): 미리보기 블록이 내려오고 draw()로 새 미리보기를 뽑음
       draw(): itemsPending > 0 이면 ItemCatalog 목록에서 균등 선택 → ItemKind.toItemPiece
  → 새 미리보기 = 아이템 블록 → EngineSnapshot.nextPiece → TextRenderer.drawNext
```

테스트: `ItemPieceSourceTest`, `AppCompositionTest`(10줄 후 미리보기 아이템)

### R2-13~14 아이템 형태와 표시

| 요구사항 | 코드 |
| --- | --- |
| 블록 일부가 아이템 | `core/item/AttachedItem.toItemPiece`: 일반 블록의 무작위 칸 번호에 아이템 (`Piece.withItem`) |
| 블록 자체가 아이템 | `core/item/SoloBlock.toItemPiece`: 일반 블록 대신 1칸 전용 블록 |
| 블록과 함께 회전 | `core/Piece`가 칸 번호로 아이템을 기억. `TetrominoType`은 회전해도 같은 번호가 같은 칸이 되도록 좌표표를 정렬 |
| 다른 문자, 반전 표시 | `Item.symbol()` → `BlockGlyphs.of(Item)` + `ColorPalette.itemStyle`(블록 색 바탕, 글자 반전) |

테스트: `SoloBlockTest`, `PieceTest`, `TetrominoTypeTest`, `ItemCatalogTest`, `TextRendererTest`

### R2-17 아이템 발동 (팀 아이템 `C`, `B`, `$`)

```
PlayerEngine.lockActivePieceAndSpawnNext
  1. Board.place(블록)                       아이템도 칸에 기록
  2. activateItems: 아이템이 실린 칸마다
       Board.removeItem(칸)                  표시 제거 (한 번 쓰고 소모)
       Item.onLock(LockContext, 칸)          효과 실행
       ItemActivation 기록                   어떤 아이템이 어디서 발동했는지
  3. Board.clearRows(아이템이 예약한 줄)      꽉 찬 줄과 함께 한 번에 삭제
  4. source.onLinesCleared → 5. spawnNext
  → EngineStep(…, clearResult, itemActivations)
```

`LockContext`는 `core/item/ItemContext`의 구현으로, 아이템이 보드에 할 수 있는 일(`cellAt`, `clearRow`, `removeCell`)만 열어 줍니다.

| 아이템 | 코드 흐름 |
| --- | --- |
| `C` 연쇄 삭제 | `core/item/ChainClearItem.onLock` → `connectedGroup`(상하좌우 너비 우선 탐색, 같은 `TetrominoType`만) → 덩어리가 4칸보다 크면 `ItemContext.removeCell` |
| `B` 폭탄 | `core/item/BombItem.onLock` → 자기 칸 중심 3×3을 `removeCell` (보드 밖 무시). 블록은 `SoloBlock`, `rotationStates() == 1`이라 `RotationSystem`이 회전을 거부 |
| `$` 점수 2배 | `core/item/DoubleScoreItem.onLock`은 보드 효과 없음 → `SinglePlayerSession.accept`의 `activatesDoubleScore`가 발동 보고를 보고 `doubleScoreClears = 3` → 줄을 지운 동작의 점수 ×2, 횟수 1 감소 → `GameSnapshot.doubleScoreClears` → `TextRenderer.drawStats`가 SCORE 옆에 `x2 (n)` |

테스트: `ChainClearItemTest`, `BombItemTest`, `DoubleScoreTest`, `TeamItemsInEngineTest`

### R2-15~16 줄 삭제·무게추

| 아이템 | 코드 흐름 |
| --- | --- |
| `L` 줄 삭제 | `LineClearItem.onLock` → `ItemContext.clearRow(position.y())` → `Board.clearRows`에서 완성 줄과 합산·중복 제거 → 일반 점수·누적 줄 수 반영 |
| `W` 무게추 | `WeightBlock.SHAPE.movementRule()` → `WeightMovementRule` 선택. `PlayerEngine.updateContact`가 최초 아래 접촉을 기억하고, 이동 규칙이 접촉 후 좌우 이동을 차단하며 회전은 항상 금지. 낙하는 `PlayerEngine.descendOneCell` → `DropRule.descend` → `WeightMovementRule.descend`에서 다음 위치의 칸 삭제·한 칸 하강 → 바닥 고정. `SinglePlayerSession.accept`는 동작 전 블록이 무게추이면 하강 점수 제외 |

두 아이템은 `ItemCatalog`에 등록되어 기존 미리보기·아이템 문자 렌더링을 사용합니다. 테스트: `RequiredItemsTest`, `RequiredItemScoringTest`, `TextRendererTest`.

### R2-18 아이템을 추가하기 쉬운 구조

고정 시 발동하는 새 아이템은 아래 두 곳을 고치면 게임에 나옵니다. 무게추처럼 이동 규칙이 다른 아이템은 `PieceMovementRule`을 구현하고 해당 `Shape.movementRule()`에서 반환합니다. 엔진은 최초 접촉 상태를 관리하고 이동·회전 허용 여부와 한 칸 낙하를 이 규칙에 위임합니다.

1. `core/item/`에 `Item` 구현 클래스 추가: `symbol()`과 `onLock(ItemContext, Position)`
2. `core/item/ItemCatalog.entries()`에 `new Entry("표시 이름", 생성 방식)` 추가 (`all()`이 게임 공급자용 목록으로 변환)
   - 블록에 붙는 아이템: `new AttachedItem(new 새아이템())`
   - 1칸 블록 아이템: `new SoloBlock(new 새아이템())`
   - 다른 모양의 블록: `ItemKind`를 구현해 자기 `Shape`의 `Piece`를 돌려줌

보드 밖에 영향을 주는 효과(점수 등)는 `EngineStep.itemActivations()`를 보고 `application`에서 처리합니다(`$` 참고).

---

## 3. R2-06 줄 삭제 애니메이션

`Board.clearRows` → `ClearResult.clearedRows()` → `SinglePlayerSession.accept`에서 점수 반영·`LineClearAnimation` 시작 → `GameSnapshot.lineClearAnimation()` → `TextRenderer.render`.

렌더러는 압축된 보드와 `ClearedRow.cells()`로 삭제 전 행 위치를 복원합니다. 삭제 행 전체를 60ms 흰색으로 강조하고 240ms 동안 가운데부터 양쪽 끝으로 지운 뒤 압축된 보드·다음 활성 블록을 표시합니다. `SinglePlayerSession.update`가 연출 시간을 진행하며 그동안 이동·회전·드롭·중력은 차단합니다. 일시정지는 연출도 멈추고, 스폰 실패에 따른 종료 결과는 연출 뒤 확정합니다. `GameScreen`은 연출 중 키 입력을 버리고 반복을 소비합니다.

아이템의 `LockContext.removeCell`과 낙하 전후 비교(`PlayerEngine.removedSince`) → `EngineStep.removedCells()`도 같은 연출에 합쳐집니다. 실제로 지운 칸만 강조하고 각 행의 삭제 범위 중앙부터 지웁니다. 개별 칸 삭제에는 점수·줄 수 증가나 위 칸 하강이 없습니다. 무게추의 낙하 중 삭제는 이전 활성 블록을 유지하고, 하드 드롭 경로의 삭제는 한 번에 연출합니다.

테스트: `LineClearAnimationTest`, `ItemClearAnimationTest`, `LineClearRenderingTest`, `GameScreenTest`.
