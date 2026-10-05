# 개발 및 API 사용 가이드

현재 API를 연결하고 수정할 때 사용하는 안내서입니다.

## 목차

- [1. 구조와 진입점](#1-구조와-진입점)
- [2. 빠른 시작과 UI 연결](#2-빠른-시작과-ui-연결)
- [3. 게임 진행 API](#3-게임-진행-api)
- [4. 종료와 이름 입력](#4-종료와-이름-입력)
- [5. 설정 화면](#5-설정-화면)
- [6. 순위 화면](#6-순위-화면)
- [7. 오류 처리](#7-오류-처리)
- [8. 정책과 확장](#8-정책과-확장)
- [9. 주요 소스와 API 참고](#9-주요-소스와-api-참고)
- [10. 빌드·테스트](#10-빌드테스트)

## 1. 구조와 진입점

설계 배경은 [아키텍처 결정 기록](architecture-decision.md)을 참고하세요. 아래는 현재 구현의 구성과 사용 경계입니다.

| 패키지        | 역할                                          | 사용 경계                                                 |
| ------------- | --------------------------------------------- | --------------------------------------------------------- |
| `ui`          | 입력, 프레임 루프, 렌더링, 화면 전환          | `ApplicationContext`로 서비스·세션을 받음. 현재 골격 상태 |
| `application` | 한 판의 진행, 점수·속도, 설정·기록, 종료 흐름 | 엔진과 저장소의 인터페이스 사용                           |
| `core`        | 보드, 블록 생성·이동·회전·고정·줄 삭제        | UI·파일·시계에 의존하지 않음                              |
| `storage`     | 설정·기록 읽기와 쓰기                         | application의 저장소 인터페이스 구현                      |
| `bootstrap`   | 실제 구현 객체 조립, 앱 진입점                | 엔진·저장소 생성과 UI 연결                                |

```text
TetrisApplication → GameUi.open(ApplicationContext)
                       ├─ newGame() → StartedGame → GameSession
                       │                            └─ TetrisEnginePort
                       ├─ settings() → SettingsService → SettingsRepository
                       ├─ scores() → ScoreboardService → ScoreRepository
                       └─ endings() → EndGameCoordinator → ScoreboardService
```

UI는 엔진·파일 저장소를 직접 생성하지 않습니다. 새 게임, 설정 화면, 순위 화면 모두 전달받은 `ApplicationContext`를 사용합니다. 세션·서비스·종료 조율자는 하나의 게임 루프 실행 흐름에서 직렬 호출합니다.

### 구성 요소를 분리한 이유

`PlayerEngine`은 입력이나 중력 처리 한 번에 따른 보드 변화를 계산하고, `SinglePlayerSession`은 경과 시간과 점수·레벨·최종 결과를 관리합니다. UI가 엔진을 직접 변경하면 세션 상태와 어긋날 수 있으므로 입력은 `GameSession`으로 전달합니다. 현재 제공되는 세션은 단일 플레이 구현입니다.

점수·속도 정책은 application에 있습니다. 코어가 반환하는 하강 거리·줄 삭제 결과를 세션이 정책에 적용하며, 정책은 UI·파일·시계에 의존하지 않는 계산으로 유지합니다.

저장소 인터페이스는 application에 정의하고 storage에서 구현합니다. 이를 통해 서비스 코드를 바꾸지 않고 파일 저장소나 테스트용 메모리 저장소를 연결할 수 있습니다. 구체 구현의 생성과 연결은 bootstrap에서 담당합니다.

`EndGameCoordinator`는 이름 입력·저장·순위 조회 상태를 반환하고, 실제 화면 전환은 UI가 담당합니다. 저장 오류가 발생해도 결과를 유지하고 재시도할 수 있도록 종료 흐름을 화면과 분리합니다.

## 2. 빠른 시작과 UI 연결

다음은 실제 공개 API를 사용하는 최소 연결 예제입니다. 화면을 표시하는 완성 UI는 아니며, `open`에서 컨텍스트를 보관하고 메뉴의 새 게임 이벤트에 해당 코드를 연결하면 됩니다.

```java
import java.nio.file.Path;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.StartedGame;
import team.tetris.bootstrap.TetrisApplication;

TetrisApplication.launch(Path.of("tetris-test-data"), application -> {
    StartedGame started = application.newGame();
    var loaded = started.settings();
    loaded.error().ifPresent(error -> System.err.println(error.getMessage()));
    var settings = loaded.value(); // 실제 UI의 크기·키·색상에 적용
    var session = started.session();

    session.handle(GameCommand.MOVE_LEFT);
    GameSnapshot view = session.snapshot();
    System.out.println("score=" + view.score());
    // 이곳에서 UI 이벤트 루프를 연결한다.
});
```

`newGame()`은 매번 독립적인 엔진·세션과 최신 저장 설정을 반환합니다. 설정 읽기에 실패해도 기본 설정으로 세션을 생성하며 `started.settings().error()`로 오류를 함께 알립니다. UI는 기본값 사용 사실을 표시해야 합니다. 보드는 10×20이며 화면 크기 설정과 무관합니다.

표준 `main`으로 실행하려면 `GameUi`를 구현하고 `open(ApplicationContext)`에서 UI를 시작합니다. public 기본 생성자가 있는 구현 클래스의 전체 이름을 다음 파일에 한 줄로 등록합니다.

```text
src/main/resources/META-INF/services/team.tetris.application.port.GameUi
```

예를 들어 구현 클래스가 `team.tetris.ui.TetrisUi`라면 파일 내용도 `team.tetris.ui.TetrisUi`입니다. 이 클래스는 예시이며 현재 저장소에는 실제 제공자가 없습니다. 테스트 리소스의 가짜 UI는 배포 JAR에 포함되지 않습니다.

개발 환경은 JDK 21과 Gradle Wrapper 8.9이며, 배포 대상은 Windows입니다. 빌드·테스트 명령은 [빌드·테스트](#10-빌드테스트)를 참고하세요. 실제 UI 제공자 등록 후 저장소 루트의 PowerShell에서 실행합니다.

```powershell
.\gradlew.bat run
.\gradlew.bat run --args='--data-dir C:/tetris-test-data'
```

기본 데이터 디렉터리는 사용자 홈의 `.se-tetris-team13`이며 `--data-dir PATH`로 변경할 수 있습니다. 설정은 `settings.properties`, 기록은 `scores.bin`에 저장합니다. 조립과 조회만으로 파일을 생성하지 않습니다.

`AppComposition`은 지정 디렉터리 아래의 설정·기록 저장소를 연결하고, 새 게임마다 `SevenBagGenerator → PlayerEngine → SinglePlayerSession`을 생성합니다.

## 3. 게임 진행 API

```java
public interface GameSession {
    void handle(GameCommand command);
    void update(long elapsedNanos);
    GameSnapshot snapshot();
    Optional<GameResult> result();
}

public enum GameCommand {
    MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, HARD_DROP, ROTATE_CW,
    PAUSE, RESUME, QUIT_GAME
}

public enum GameStatus {
    RUNNING, PAUSED, GAME_OVER, ABORTED
}

public record GameSnapshot(
    EngineSnapshot engine,
    long score,
    int level,
    int clearedLines,
    long gravityIntervalNanos,
    GameStatus status
) {}
```

- 스냅샷의 `engine`은 기존 코어 타입이며 보드·현재 블록·다음 블록을 포함한다. UI는 코어 메서드를 직접 호출하지 않는다.
- 현재 코어의 `Cell[][]`는 배열이므로 record 선언만으로 완전한 불변성이 보장되지는 않는다. 생성·조회 시 보드 데이터의 방어적 복사를 제공하며 UI는 읽기 전용으로 사용한다.
- 게임오버 시 `activePiece`가 null일 수도, 마지막 블록이 남아 있을 수도 있다. UI는 null을 처리하고 세션의 `status`를 화면 전환 기준으로 사용한다.
- 생성된 세션은 RUNNING으로 시작한다. 새 게임은 새 엔진·세션을 생성하며 이전 세션을 재사용하지 않는다.
- `SinglePlayerSession(TetrisEnginePort)`는 RUNNING인 새 엔진을 받는다. null 또는 RUNNING이 아닌 엔진은 거부한다. 엔진은 세션이 소유하며 외부에서 직접 조작하지 않는다.
- `SinglePlayerSession(TetrisEnginePort, ScoreRule, SpeedRule)`로 점수·속도 규칙을 주입할 수 있다. 기본 생성자는 아래의 `ScorePolicy`와 `SpeedPolicy`를 사용한다. 정책은 순수 계산으로 구현하며 실행 중 외부에서 변경하지 않는다.
- `result()`는 진행·정지 중 `Optional.empty()`이고 종료 후 한 번 확정한 동일 `GameResult`를 반환한다. 결과 필드는 `UUID gameId`, `long score`, `int level`, `int clearedLines`, `GameStatus reason`이며 종료 사유는 GAME_OVER 또는 ABORTED다.
- `QUIT_GAME`은 현재 판을 ABORTED로 종료한다. 프로그램 종료와 메뉴 복귀는 UI 라우터가 구분한다.
- RUNNING에서는 조작·PAUSE·QUIT_GAME을 허용한다. PAUSED에서는 RESUME·QUIT_GAME만 허용한다. 종료 상태에서는 모든 명령과 시간 갱신이 상태를 바꾸지 않는다.
- null 명령과 음수 경과 시간은 프로그래밍 오류로 거부한다. `update(0)`은 진행하지 않는다.

### 시간·스레드 계약

1. UI 루프가 `System.nanoTime()` 같은 단조 증가 시계로 경과 시간을 측정한다. 시스템 날짜·시각을 중력 계산에 사용하지 않는다.
2. UI가 `update(elapsedNanos)`를 호출하고, 세션만 주기를 판단하여 `engine.tick()`을 호출한다. 한 프레임은 한 번의 낙하를 뜻하지 않는다.
3. UI는 PAUSED 중에도 루프의 직전 시각을 갱신한다. 세션은 정지 중 전달된 시간을 버린다.
4. PAUSE 및 RESUME 시 중력 누적 시간을 0으로 만든다. 재개 후 현재 주기 전체가 지난 뒤 처음 낙하한다.
5. 입력은 루프마다 큐에 들어온 순서대로 처리하고 다음 중력 시점까지 기다리지 않는다. 키 반복 생성은 UI의 InputMapper/RepeatController 책임이다.
6. `update`, `handle`, `snapshot`은 하나의 게임 루프 스레드에서 직렬 호출한다. 별도 입력 스레드는 명령 큐에만 추가한다.
7. 한 update에서 허용하는 자동 낙하는 최대 5회로 한다. 시작 시 누적 시간을 현재 주기의 5배 이하로 제한하고, 5회 후에도 한 주기 이상 남으면 완전한 주기분을 버리고 나머지만 유지한다. 긴 지연 뒤 과도한 따라잡기를 막는 현재 구현값이며, 변경 여부는 미확정 사항에서 관리한다.
8. 누적 시간 덧셈은 상한을 넘기기 전에 제한하여 오버플로를 방지한다. GAME_OVER가 발생하면 남은 자동 낙하 처리를 즉시 중단한다.
9. 블록이 고정되어 다음 블록으로 전환되면 중력 누적 시간을 초기화하고 해당 update의 따라잡기를 종료한다. 하드드롭 직후 새 블록에도 한 주기를 보장한다. 단순 좌우 이동·회전·성공한 소프트드롭은 중력 타이머를 초기화하지 않는다.

### UI 루프 호출 예시

아래는 호출 순서를 보여주는 의사 코드다. 큐·렌더러·세션 생성 함수의 실제 선언은 UI 구현에 맞춘다.

```java
long previous = clock.nanoTime();
while (gameScreenActive) {
    long now = clock.nanoTime();
    session.update(now - previous); // 직전 구간의 시간부터 반영
    previous = now;

    while (commands.hasNext()) {
        session.handle(commands.next());
    }

    GameSnapshot view = session.snapshot();
    renderer.render(view);
    // 종료 상태이면 coordinator를 통해 다음 화면 결정
    // UI 프레임 대기/이벤트 대기는 UI 루프에서 수행
}
```

시간 갱신 후 입력을 처리하므로 재개 명령 이전의 정지 시간이 새 세션 진행에 섞이지 않는다. 메뉴나 이름 입력 화면에서 돌아올 때도 UI의 직전 시각을 새로 잡는다. 세션 내부에서 sleep하거나 입력을 기다리지 않는다.

### 화면 데이터 읽기

`view.engine()`을 프레임당 한 번 받아 사용합니다. `board()[y][x]`는 고정된 셀이며 행 0이 맨 위입니다. 낙하 중인 블록은 `activePiece`를 별도로 그립니다. 각 칸의 위치는 `activePiece.origin()`에 `activePiece.type().cellsAt(activePiece.rotation())`의 오프셋을 더해 구합니다. 보드 위쪽의 음수 y좌표는 화면에서 제외합니다. `nextType()`은 다음 블록 미리보기입니다.

`GameSnapshot`은 생성·조회 시 보드 배열을 복사합니다. 코어의 `EngineSnapshot` record 자체에 방어 복사 기능이 있는 것은 아닙니다. 화면에서는 반환 데이터를 읽기 전용으로 취급하고, 종료 화면 전환은 엔진 phase 대신 세션 `status()`와 `result()`를 기준으로 판단합니다.

## 4. 종료와 이름 입력

게임 종료를 감지하면 게임 화면의 루프에서 종료 화면으로 한 번 전환합니다. 아래 코드는 각 UI 이벤트에 연결할 호출 조각이며, `session`, `application`, `nameFromInput`은 UI가 보관한 값입니다.

```java
// 게임 종료 감지 시
var result = session.result().orElseThrow();
var ending = application.endings().begin(result);
// ending.stage(), nameError(), storageError()를 읽어 다음 화면을 결정한다.

// NAME_REQUIRED 화면의 이름 제출 이벤트
ending = application.endings().submitName(result.gameId(), nameFromInput);

// 이름 검증 또는 저장 실패 후: 같은 gameId와 수정한 이름으로 submitName 재호출
// CHECKING 또는 저장 성공 후 순위 조회 실패: 같은 result로 begin 재호출
```

일반적인 화면 구현에서는 종료 조율자를 통해 등록합니다. 순위 서비스의 `register()`까지 별도로 호출할 필요는 없습니다.

`EndGameCoordinator.begin(GameResult)`와 `submitName(UUID gameId, String name)`은 `EndGameView` 반환.
결과는 게임별로 보관하므로 이름 입력 또는 저장 실패 후 같은 게임 ID로 재시도 가능.

| stage           | 의미                                   | UI의 다음 동작                                 |
| --------------- | -------------------------------------- | ---------------------------------------------- |
| CHECKING        | 순위 조회 실패로 등록 대상 판단 미완료 | 오류 표시 후 `begin(result)` 재호출            |
| NAME_REQUIRED   | 이름 입력 필요                         | 입력 후 `submitName(gameId, name)` 호출        |
| SHOW_SCOREBOARD | 등록 또는 비대상 판단 완료             | scores 표시, highlightedRecordId가 있으면 강조 |
| RETURN_MENU     | 중도 종료                              | 이름 입력 없이 메뉴 복귀 또는 프로그램 종료    |

`nameError`는 이름 검증 오류, `storageError`는 기존 `StorageException` 오류 제공.
두 오류가 모두 비어 있는 경우 정상 처리. 순위 목록은 수정 불가능한 값.
중도 종료는 저장소를 읽거나 쓰지 않고 RETURN_MENU 반환.

### 실패와 중복 요청

- 이름 오류: NAME_REQUIRED 유지, 올바른 이름으로 `submitName` 재호출.
- 저장 실패: NAME_REQUIRED와 저장 오류 반환, 원본 결과 보존 후 같은 ID로 재시도.
- 저장 성공 후 순위 재조회 실패: SHOW_SCOREBOARD와 읽기 오류 반환. 저장된 ID를 내부 보관하며 `begin` 또는 `submitName` 재호출 시 조회만 재시도.
- 같은 결과의 중복 begin/submitName: 성공한 응답 재사용, 추가 기록 저장 없음.
- 새 조율자에서 이미 저장된 결과 begin: 저장소의 게임 ID별 등록 이력 확인 후 이름 입력 생략.
- 순위에서 밀려난 기록: 기존 등록 사실 유지, 현재 목록에 없는 기록 ID 강조 생략.
- 이름 입력 도중 순위 변동: 서비스에서 재판단하고 미진입이면 강조 없이 SHOW_SCOREBOARD 반환.
- 알 수 없는 게임 ID로 submitName 또는 같은 ID의 서로 다른 결과로 begin: 프로그래밍 오류로 거부.

CHECKING에서 submitName 호출은 거부하며 먼저 begin으로 조회 재시도 필요.
NAME_REQUIRED에서 begin을 반복해도 이름 입력을 자동 완료하지 않음.
완료 응답은 해당 종료 시점의 목록으로 유지. 메뉴에서 최신 순위를 보려면 `scores().list()` 호출.
조율자의 완료 이력은 조율자 수명 동안 보관. 앱 재시작 시 영속 등록 이력으로 중복 판별.
이름 입력 취소는 현재 범위에 미포함.

## 5. 설정 화면

```java
var settings = application.settings();
Settings current = settings.get();
settings.update(new Settings(Settings.ScreenSize.LARGE, current.keyBindings(), true));
settings.reset();
```

- `get()`: 저장된 설정 조회. 파일이 없으면 기본 설정 반환. 조회 중 파일 생성 없음.
- `update(settings)`: 검증된 설정 저장. 정상 반환한 뒤 UI에 적용.
- `reset()`: 기본 설정 저장. 기록 파일에는 영향 없음.
- `loadOrDefault()`: 읽기 실패 시 기본값과 오류를 `LoadResult`로 함께 반환. 원본 자동 수정 없음.
- `SettingsService(repository, defaults)`: 별도의 기본 설정 주입 지원.

기본값은 `Settings.defaults()` 한 곳에서 정의. 기존 빈 `default_config.json`은 로드하지 않음.
화면 크기는 SMALL/MEDIUM/LARGE 중 선택, 기본 MEDIUM. 색맹 모드는 기본 false.
실제 창·폰트·셀 크기와 색상/무늬 표현은 UI 책임.

| 명령                   | 기본 키 식별자 |
| ---------------------- | -------------- |
| MOVE_LEFT / MOVE_RIGHT | LEFT / RIGHT   |
| SOFT_DROP / ROTATE_CW  | DOWN / UP      |
| HARD_DROP              | SPACE          |
| PAUSE / RESUME         | P              |
| QUIT_GAME              | ESCAPE         |

모든 `GameCommand`의 키 지정 필수. 식별자는 `[A-Z][A-Z0-9_]*` 형식으로 제한.
허용되는 실제 키 목록과 이벤트 변환은 UI와 추가 확인 필요.
RUNNING 명령끼리의 키 충돌 및 PAUSED에서 RESUME과 QUIT_GAME의 충돌 거부.
PAUSE와 RESUME은 문맥이 달라 같은 키 사용 가능.
키 매핑은 방어 복사 후 수정 불가능한 형태로 제공.

설정 화면에서 읽기 실패를 표시하면서 기본값을 보여주려면 `application.settings().loadOrDefault()`의 `value()`와 `error()`를 함께 사용합니다. 저장 버튼에서는 새 `Settings` 생성 시 검증 오류와 `update()`의 저장 오류를 처리한 뒤, 정상 반환했을 때만 화면에 적용합니다. 기존 판의 설정이 자동 갱신되지는 않으며, 다음 `newGame()`은 최신 저장값을 읽습니다.

## 6. 순위 화면

메뉴에서 순위만 표시하려면 `application.scores().list()`를 호출합니다. 읽기 오류와 빈 목록을 함께 받고 싶다면 `loadOrEmpty()`를 사용하고 오류를 별도로 표시합니다. 아래 등록 예제는 종료 조율자 없이 별도 흐름을 구성할 때의 하위 API입니다.

```java
var scores = application.scores();
if (scores.qualifies(result)) {
    // UI에서 이름 입력 후 등록 시점의 순위 재판단.
    Optional<UUID> recordId = scores.register(result, name);
}
List<ScoreEntry> entries = scores.list();
```

- `list()`: 점수 내림차순의 수정 불가능한 목록 반환.
- `registeredRecordId(gameId)`: 기존 등록 이력의 기록 ID 조회. 종료 조율자 재생성 시 중복 등록 확인.
- `qualifies(result)`: 이름 입력 전 참고 판단. 이미 등록된 게임 또는 등록 대상이 아닌 종료 사유는 false.
- `register(result, name)`: 저장 직전 데이터 재조회 및 순위 재판단. 저장 성공 시 기록 ID, 미진입 시 empty 반환.
- `clear()`: 목록과 등록 이력을 함께 삭제. 저장 성공 후 화면 갱신. 설정 파일에는 영향 없음.
- `loadOrEmpty()`: 읽기 실패 시 빈 목록과 오류를 함께 반환. 원본 자동 수정 없음.
- `ScoreboardService(repository, policy, clock)`: 순위 정책과 등록 시각용 시계 주입 지원.

기본 정책은 상위 10개, 이름 1~12 Unicode 코드 포인트, 자연 게임오버만 등록.
`ScoreboardPolicy`로 보관 개수(최소 10), 이름 길이, 중도 종료 허용 여부 변경 가능.
이름은 앞뒤 공백 제거 후 길이 검증. 제어 문자는 앞뒤 위치와 무관하게 거부.

동점은 **등록 순서** 유지. 점수와 등록 시각으로 재정렬하지 않으므로 시계 역행에도 순서 유지.
정원이 찼을 때 마지막 기록과 같은 점수의 신규 진입 거부.
`ScoreEntry`는 recordId, gameId, name, score(long), registeredAt(Instant) 보관.
이름 길이 정책은 신규 등록 시 적용하며 기존 기록을 정책 변경만으로 삭제하지 않음.

동일 게임 ID 재등록은 최초 기록 ID 반환. 새 이름이나 점수로 기존 기록 변경 없음.
순위에서 밀려난 게임의 ID도 `ScoreStore.registrations`에 유지하여 재실행 후 중복 등록 방지.
반환된 ID가 현재 순위 목록에 없을 수 있으므로 UI의 강조 대상 존재 확인 필요.
등록 이력은 명시적 `clear()`까지 유지하며 초기화 후 이전 게임 재등록 가능.
이력의 장기 용량 제한·정리 정책은 후속 협의 대상.

## 7. 오류 처리

모델·이름 검증 실패는 `IllegalArgumentException`, 필수 인자의 null은 `NullPointerException`.
저장소 실패는 경로와 원인을 포함하는 `StorageException`으로 전달.

| `StorageException.Kind` | 의미                                 | UI 처리 예시                         |
| ----------------------- | ------------------------------------ | ------------------------------------ |
| READ_FAILED             | 파일 읽기 실패                       | 오류 안내 후 재시도 또는 임시값 사용 |
| INVALID_DATA            | 파일 형식·필수 필드·모델 무결성 오류 | 원본 보존 및 복구 안내               |
| UNSUPPORTED_VERSION     | 지원하지 않는 schemaVersion          | 버전 확인 안내                       |
| WRITE_FAILED            | 임시 파일 작성 또는 교체 실패        | 기존 화면값 유지 및 재시도           |

`loadOrDefault()`와 `loadOrEmpty()`의 `error`를 확인하여 임시값 사용 사실을 표시할 책임은 UI에 있음.
임시값 반환 자체는 저장 성공이나 원본 복구를 의미하지 않음.
손상 파일은 읽기만으로 덮어쓰거나 백업·삭제하지 않음.
사용자가 명시적으로 설정 저장·초기화 또는 순위 초기화를 요청하면 해당 파일 교체 가능.
손상된 순위 파일에 대한 일반 기록 등록은 읽기 오류로 중단. 임시 빈 목록에 자동 덮어쓰기 없음.

서비스는 저장 실패한 변경을 캐시하지 않으며 원본 `GameResult`를 변경하지 않음.
종료 조율자 또는 UI에서 결과를 보존하면 같은 결과로 재시도 가능.

## 8. 정책과 확장

### 현재 기본 정책

다음 수치는 현재 구현의 기본값이며, 팀 합의가 필요한 기준안이다.

```text
level = totalClearedLines / 10                 // 초기 0
gravityIntervalMs = max(100, 1000 - level × 100)
dropScore = actualDroppedCells × (levelBeforeStep + 1)
lineBonus = 100 × clearedLinesInThisStep²
```

- 자동 낙하, 소프트드롭, 하드드롭 모두 실제 하강 칸 수만큼 점수를 얻는다.
- 이동 실패·회전·좌우 이동·하강 거리 0에는 낙하 점수가 없다.
- 초기 칸당 1점, 가속 이후 추가 점수를 부여한다. 줄 삭제 보너스는 별도의 추가 점수 방식이다.
- 점수는 long으로 누적한다. 레벨 9 이상에서도 낙하 주기는 최소 100ms를 유지한다.

### 정책 교체 계약

- `ScoreRule.scoreFor(droppedCells, clearedLines, levelBeforeStep)`: 동작 1회의 음이 아닌 점수 반환.
- `SpeedRule.levelFor(totalClearedLines)`: 누적 삭제 줄 수에 대한 음이 아닌 레벨 반환.
- `SpeedRule.gravityIntervalNanos(level)`: 해당 레벨의 양수 낙하 간격 반환.
- 초기 레벨도 주입한 규칙의 `levelFor(0)`으로 계산. 현재 기본 정책에서는 0 반환.
- 점수·통계는 동작 직전 레벨로 점수를 계산한 후 갱신. 음수 점수·레벨 또는 0 이하 간격은 `IllegalStateException`, 표현 범위를 넘는 점수 누적은 `ArithmeticException`으로 거부.
- 정책 오류는 구현 계약 위반이며 동일 세션의 재시도 대상이 아님. 이미 진행된 코어 동작을 되돌리는 기능은 제공하지 않음.
- 사용자 정의 낙하 간격이 매우 커도 시간 상한의 곱셈·덧셈 오버플로 방지. update당 최대 5회와 블록 고정 시 초기화 규칙은 동일하게 적용.

점수·속도 공식은 여전히 구현 기준안이며 팀 합의 완료를 의미하지 않는다. 기본 공식 변경은 정책 구현에서, 대체 공식 연결은 생성자 주입으로 처리한다.

### 코어 결과를 소비하는 순서

1. 코어 호출 직전 레벨을 보관한다.
2. `apply(action)` 또는 `tick()`을 호출한다.
3. `dropResult`가 없으면 하강 거리를 0, `clearResult`가 없으면 삭제 줄 수를 0으로 읽는다. 결과 객체가 있어도 값은 0일 수 있다.
4. 직전 레벨로 이번 획득 점수를 계산하여 정확히 한 번 더한다.
5. 누적 삭제 줄 수를 갱신하고 다음 동작에 적용할 레벨·주기를 계산한다.
6. 코어 phase를 반영한다. 게임오버를 만든 마지막 동작의 점수도 먼저 반영하고 결과를 확정한다.

예: 누적 9줄·레벨 0에서 하드드롭으로 3칸 하강하고 1줄을 지우면 3+100점을 얻는다. 이후 누적 10줄·레벨 1·900ms 주기가 적용된다.

정지·재개는 코어에도 PAUSE/RESUME을 전달하여 세션과 엔진 상태를 맞춘다. ABORTED는 세션 상태이며 이후 엔진 호출을 차단한다.

### 확장 지점

- 점수·속도: `ScoreRule`과 `SpeedRule`을 구현하고 `AppComposition(Path, Supplier<? extends PieceGenerator>, ScoreRule, SpeedRule)`에 전달합니다. 순수 계산으로 구현하며 실행 중 외부에서 변경하지 않습니다.
- 블록 생성: `PieceGenerator`를 구현하고 위 생성자에 팩터리를 전달합니다. 새 게임마다 독립된 생성기를 반환해야 합니다.
- 코어: `TetrisEnginePort`는 `snapshot()`, `apply(GameAction)`, `tick()`을 제공합니다. 세션은 이 포트에 의존하며 실제 엔진 생성은 bootstrap에서 수행합니다. `EngineStep`의 drop/lock/clear 결과는 없으면 null일 수 있습니다.
- 저장 방식: `SettingsRepository` 또는 `ScoreRepository`를 구현해 서비스 생성자에 전달합니다. 앱 전체에 적용하려면 bootstrap 조립도 변경합니다. 테스트용 메모리 구현은 `storage.memory`에 있습니다.
- 순위 정책: `ScoreboardService(repository, policy, clock)`으로 보관 개수·이름 제한 등을 주입합니다. 현재 종료 조율자는 ABORTED를 항상 메뉴로 보내므로, 중도 종료 등록 정책을 바꿀 경우 종료 흐름도 함께 검토해야 합니다.

현재 파일 저장소는 Java 기본 API로 설정을 Properties, 기록을 바이너리 형식에 저장합니다. 이전 파일 형식의 자동 변환은 제공하지 않습니다. 기존 파일을 보존하기 위해 같은 디렉터리의 임시 파일을 원자적으로 교체합니다. 원자적 교체가 지원되지 않으면 `WRITE_FAILED`이며 비원자적 덮어쓰기로 전환하지 않습니다. 여러 프로세스의 동시 쓰기는 보장하지 않습니다.

## 9. 주요 소스와 API 참고

| 작업                     | 진입 API 및 소스                                                                                                                                                      |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| UI 시작·등록             | [GameUi](../src/main/java/team/tetris/application/port/GameUi.java), [TetrisApplication](../src/main/java/team/tetris/bootstrap/TetrisApplication.java)               |
| 서비스 획득·새 게임      | [ApplicationContext](../src/main/java/team/tetris/application/ApplicationContext.java), [StartedGame](../src/main/java/team/tetris/application/StartedGame.java)      |
| 명령·시간·표시·종료 결과 | [GameSession](../src/main/java/team/tetris/application/GameSession.java), [GameSnapshot](../src/main/java/team/tetris/application/GameSnapshot.java)                  |
| 종료 화면과 재시도       | [EndGameCoordinator](../src/main/java/team/tetris/application/EndGameCoordinator.java), [EndGameView](../src/main/java/team/tetris/application/EndGameView.java)      |
| 설정 조회·저장·초기화    | [SettingsService](../src/main/java/team/tetris/application/SettingsService.java), [Settings](../src/main/java/team/tetris/application/model/Settings.java)            |
| 순위 조회·등록·초기화    | [ScoreboardService](../src/main/java/team/tetris/application/ScoreboardService.java)                                                                                  |
| 읽기 대체값·오류         | [LoadResult](../src/main/java/team/tetris/application/model/LoadResult.java), [StorageException](../src/main/java/team/tetris/application/port/StorageException.java) |
| 코어 연동                | [TetrisEnginePort](../src/main/java/team/tetris/core/port/TetrisEnginePort.java), [EngineStep](../src/main/java/team/tetris/core/result/EngineStep.java)              |
| 구현 객체 조립           | [AppComposition](../src/main/java/team/tetris/bootstrap/AppComposition.java)                                                                                          |

API 변경 시 이 문서의 호출 예제와 관련 테스트를 함께 확인합니다. 현재 기본값 변경과 팀의 합의 여부는 [미확정 사항](open-questions.md)에도 반영합니다.

## 10. 빌드·테스트

JDK 21과 Gradle Wrapper를 사용합니다. 저장소 루트의 Windows PowerShell에서 실행합니다. 최초 실행에는 Gradle 및 테스트 의존성을 다운로드할 네트워크가 필요합니다.

```powershell
.\gradlew.bat --no-daemon clean build jacocoTestReport
```

`build`는 테스트와 배포물 생성을 포함합니다. 의존성이 캐시되어 있으면 `--offline`을 추가할 수 있습니다. 특정 테스트만 확인할 때는 다음과 같이 실행합니다.

```powershell
.\gradlew.bat test --tests 'team.tetris.application.SinglePlayerSessionTest'
```

| 결과             | 경로                                        |
| ---------------- | ------------------------------------------- |
| 테스트 HTML      | `build/reports/tests/test/index.html`       |
| JUnit XML        | `build/test-results/test/`                  |
| 커버리지 HTML    | `build/reports/jacoco/test/html/index.html` |
| 커버리지 XML·CSV | `build/reports/jacoco/test/`                |
| 배포 ZIP·TAR     | `build/distributions/`                      |

커버리지 최소값은 현재 빌드에서 강제하지 않습니다. 테스트 개수와 커버리지는 해당 실행의 보고서에서 확인합니다.

### 배포 패키지 (실행 파일)

`jpackage`로 JRE를 포함한 Windows 실행 폴더를 만듭니다. Java가 설치되지 않은 PC에서도 `SETetris.exe`를 더블클릭하면 실행되며, exe와 창에는 `src/main/resources/icon.ico`·`icon.png` 아이콘이 들어갑니다. 설치 프로그램 방식(`--type exe`)이 아니므로 WiX가 필요 없습니다.

```powershell
.\gradlew.bat packageZip
```

| 결과                  | 경로                                              |
| --------------------- | ------------------------------------------------- |
| 실행 폴더             | `build/jpackage/SETetris/` (`SETetris.exe`, `app/`, `runtime/`) |
| 배포용 zip            | `build/distributions/SETetris-<version>-windows.zip` |

zip을 풀고 `SETetris\SETetris.exe`를 실행합니다. `runtime\`·`app\` 폴더가 exe와 같은 위치에 있어야 합니다. 실행 폴더만 필요하면 `.\gradlew.bat packageApp`을 씁니다. 포함 모듈은 jdeps 결과(`java.base`, `java.desktop`)로 줄였고, 최소 사양(RAM 1GB)을 고려해 힙 상한을 256MB로 둡니다. 저장 데이터는 exe 위치가 아니라 사용자 홈의 `.se-tetris-team13/`에 남습니다.

### CI와 화면 검증

[Windows Java CI](../.github/workflows/ci.yml)는 push·pull request·수동 실행 시 Windows와 Java 21에서 빌드·테스트·커버리지를 생성합니다. 생성된 보고서는 `test-and-coverage-reports` 아티팩트로 14일 보관합니다. 원격 성공 여부는 해당 커밋의 Actions 결과로 확인합니다.

CI에는 화면을 여는 `run`이 포함되지 않습니다. 현재 가짜 UI 통합 테스트가 통과해도 실제 화면·키 입력·배포 검증이 끝난 것은 아닙니다. 실제 `GameUi` 제공자를 등록하기 전에는 표준 `run`이 제공자 미등록 오류로 종료합니다.

UI 연결 후 사용자 데이터와 분리된 경로에서 실행하고 아래 항목을 확인합니다. 이 목록은 검증 절차이며 통과 기록이 아닙니다.

```powershell
.\gradlew.bat run --args='--data-dir C:/tetris-test-data'
```

| 시나리오                 | 확인 내용                                                      |
| ------------------------ | -------------------------------------------------------------- |
| 새 게임·입력             | 보드·다음 블록·점수 표시, 키 반복과 조작 순서                  |
| 정지·재개·하드드롭       | 정지 중 진행 없음, 재개·새 블록의 낙하 주기 보장               |
| 자연 종료·이름 입력·순위 | 이름 오류 후 재입력, 최종 점수 저장, 기록 강조, 중복 저장 방지 |
| 중도 종료                | 이름 입력 없이 메뉴 복귀, 기록 추가 없음                       |
| 설정·기록 초기화·재실행  | 설정과 기록의 독립성, 저장값 복원                              |
| 읽기·쓰기 실패와 재시도  | 오류 표시, 원본 보존, 결과 유지, 저장 성공 후 조회만 재시도    |

검증 결과에는 커밋, OS·Java 버전, 실행 명령, 실제 결과와 남은 항목을 기록합니다. 상세 오류 재현·배포 절차는 실제 UI와 패키지 방식이 준비되면 보강합니다.
