# 팀원 3 연동 규약 및 협의 사항

갱신일: 2026-09-26 · 팀 공유용

## 목차

- [1. 문서 범위와 구현 상태](#1-문서-범위와-구현-상태)
- [2. 담당 범위와 협업 경계](#2-담당-범위와-협업-경계)
- [3. 구현 클래스](#3-구현-클래스)
- [4. 팀원 1에게 제공할 세션 API](#4-팀원-1에게-제공할-세션-api)
- [5. 점수·속도 정책](#5-점수속도-정책)
- [6. 설정 API](#6-설정-api)
- [7. 기록 API](#7-기록-api)
- [8. 오류와 임시 실행](#8-오류와-임시-실행)
- [9. 종료 조율자](#9-종료-조율자)
- [10. 실행 조립부](#10-실행-조립부)
- [11. UI 연결](#11-ui-연결)
- [12. 협의 및 확인 사항](#12-협의-및-확인-사항)

## 1. 문서 범위와 구현 상태

팀원 1·2가 팀원 3의 코드에 연결할 때 사용하는 공유 문서.
세션, 설정·기록 서비스, 파일 저장소, 종료 조율자, 실행 조립부 구현 완료.
실제 UI 화면·게임 루프 연결은 남아 있으며 현재 검증은 가짜 UI 통합 테스트까지 수행.

점수·속도, 이름·동점, 시간 처리 수치는 현재 구현 기준안. 팀 전체 합의 완료를 의미하지 않음.
합의 후 API·코드와 이 문서를 함께 갱신.

개발 환경은 Java 21, Gradle Wrapper 8.9. JUnit은 테스트에만 사용하며 실행 시 외부 라이브러리 없음.
검증 명령은 `./gradlew --no-daemon clean build jacocoTestReport`.
2026-09-26 기준 전체 146개 테스트 통과. 실제 UI·대상 OS 배포 검증은 별도 수행 필요.


## 2. 담당 범위와 협업 경계

| 영역 | 팀원 3 | 다른 팀원과의 경계 |
|---|---|---|
| application | 세션, 명령 처리, 시간 누적, 점수·속도, 종료 흐름, 설정·기록 서비스 | UI는 입력과 표시, 코어는 보드 규칙 담당 |
| storage | 설정·기록 파일 읽기/쓰기, 데이터 검증, 저장 오류 전달 | 파일 형식 구현과 저장 경로를 UI에 노출하지 않음 |
| bootstrap | 엔진·저장소·서비스·UI 조립, 새 세션 생성 | UI 구체 객체 생성 방식은 팀원 1과 연결 |
| 테스트 | application/storage 단위·통합 테스트 | 성능 테스트는 팀원 1 담당, 팀원 3은 세션 연동 지원 |
| 공용 기반 | 빌드·CI·배포에 필요한 요구 정리 및 협업 | 공용 파일 변경 담당과 대상 OS는 별도 확정 |

코어의 `TetrisEnginePort`에 의존하고 `PlayerEngine` 생성은 `AppComposition`에서 수행한다. 세션은 충돌, 회전, 줄 삭제, 블록 생성 규칙을 중복 구현하지 않는다.

```text
UI 입력/루프 → GameSession → TetrisEnginePort
                  ├─ ScorePolicy / SpeedPolicy
                  └─ GameSnapshot → UI 표시

설정 화면 → SettingsService → SettingsRepository ← PropertiesSettingsRepository
종료 화면 → EndGameCoordinator → ScoreboardService → ScoreRepository ← BinaryScoreRepository

AppComposition: 실제 구현 객체 조립 및 세션 생성
```

## 3. 구현 클래스

| 클래스 | 책임 |
|---|---|
| GameSession / SinglePlayerSession | 명령·시간 갱신 API, 한 판의 상태와 누적값 관리 |
| GameCommand / GameStatus | UI 명령, 세션 상태 정의 |
| GameSnapshot / GameResult | 표시용 현재 상태, 종료 시 확정 결과 |
| ScoreRule / ScorePolicy | 하강 거리·줄 삭제·동작 전 레벨에 대한 점수 계산 |
| SpeedRule / SpeedPolicy | 누적 삭제 줄 수에 대한 레벨·낙하 주기 계산 |
| SettingsService / Settings | 설정 조회·검증·저장·기본값 복원 |
| ScoreboardService / ScoreEntry / ScoreStore | 등록 가능 여부·정렬·상위 기록·초기화 |
| EndGameCoordinator / EndGameView | 종료 결과를 이름 입력·기록 저장·순위 표시로 연결 |
| SettingsRepository / ScoreRepository | 데이터 로드·저장 인터페이스 |
| PropertiesSettingsRepository / BinaryScoreRepository | 파일 기반 구현 |
| ApplicationContext / StartedGame / GameUi | UI에 서비스·새 게임 제공 및 시작 경계 |
| AppComposition / TetrisApplication | 객체 조립·프로그램 시작 |

`ScorePolicy`와 `SpeedPolicy`는 `application`에 배치했다. 비어 있던 `core/rule/ScorePolicy.java`와 대응 테스트를 제거하고 application 위치에 구현했다. 정책 위치 변경은 팀원 2와 확인 필요. 정책 클래스는 파일·시계·UI에 의존하지 않는 계산 전용 클래스다.

## 4. 팀원 1에게 제공할 세션 API

### 4.1 공개 타입 기준안

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

### 4.2 시간·스레드 계약

1. UI 루프가 `System.nanoTime()` 같은 단조 증가 시계로 경과 시간을 측정한다. 시스템 날짜·시각을 중력 계산에 사용하지 않는다.
2. UI가 `update(elapsedNanos)`를 호출하고, 세션만 주기를 판단하여 `engine.tick()`을 호출한다. 한 프레임은 한 번의 낙하를 뜻하지 않는다.
3. UI는 PAUSED 중에도 루프의 직전 시각을 갱신한다. 세션은 정지 중 전달된 시간을 버린다.
4. PAUSE 및 RESUME 시 중력 누적 시간을 0으로 만든다. 재개 후 현재 주기 전체가 지난 뒤 처음 낙하한다.
5. 입력은 루프마다 큐에 들어온 순서대로 처리하고 다음 중력 시점까지 기다리지 않는다. 키 반복 생성은 UI의 InputMapper/RepeatController 책임이다.
6. `update`, `handle`, `snapshot`은 하나의 게임 루프 스레드에서 직렬 호출한다. 별도 입력 스레드는 명령 큐에만 추가한다.
7. 한 update에서 허용하는 자동 낙하는 최대 5회로 한다. 시작 시 누적 시간을 현재 주기의 5배 이하로 제한하고, 5회 후에도 한 주기 이상 남으면 완전한 주기분을 버리고 나머지만 유지한다. 긴 지연 뒤 과도한 따라잡기를 막기 위한 제안값이며 UI 통합 테스트로 확인한다.
8. 누적 시간 덧셈은 상한을 넘기기 전에 제한하여 오버플로를 방지한다. GAME_OVER가 발생하면 남은 자동 낙하 처리를 즉시 중단한다.
9. 블록이 고정되어 다음 블록으로 전환되면 중력 누적 시간을 초기화하고 해당 update의 따라잡기를 종료한다. 하드드롭 직후 새 블록에도 한 주기를 보장한다. 단순 좌우 이동·회전·성공한 소프트드롭은 중력 타이머를 초기화하지 않는다.

### 4.3 UI 루프 호출 예시

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

## 5. 점수·속도 정책

### 5.1 초기 정책값

다음 수치는 Req1이 지정한 값이 아니라 이 구현의 기준안이다.

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

### 5.2 교체 가능한 정책 계약

- `ScoreRule.scoreFor(droppedCells, clearedLines, levelBeforeStep)`: 동작 1회의 음이 아닌 점수 반환.
- `SpeedRule.levelFor(totalClearedLines)`: 누적 삭제 줄 수에 대한 음이 아닌 레벨 반환.
- `SpeedRule.gravityIntervalNanos(level)`: 해당 레벨의 양수 낙하 간격 반환.
- 초기 레벨도 주입한 규칙의 `levelFor(0)`으로 계산. 현재 기본 정책에서는 0 반환.
- 점수·통계는 동작 직전 레벨로 점수를 계산한 후 갱신. 음수 점수·레벨 또는 0 이하 간격은 `IllegalStateException`, 표현 범위를 넘는 점수 누적은 `ArithmeticException`으로 거부.
- 정책 오류는 구현 계약 위반이며 동일 세션의 재시도 대상이 아님. 이미 진행된 코어 동작을 되돌리는 기능은 제공하지 않음.
- 사용자 정의 낙하 간격이 매우 커도 시간 상한의 곱셈·덧셈 오버플로 방지. update당 최대 5회와 블록 고정 시 초기화 규칙은 동일하게 적용.

점수·속도 공식은 여전히 구현 기준안이며 팀 합의 완료를 의미하지 않는다. 기본 공식 변경은 정책 구현에서, 대체 공식 연결은 생성자 주입으로 처리한다.

### 5.3 코어 결과 처리 순서

1. 코어 호출 직전 레벨을 보관한다.
2. `apply(action)` 또는 `tick()`을 호출한다.
3. `dropResult`가 없으면 하강 거리를 0, `clearResult`가 없으면 삭제 줄 수를 0으로 읽는다. 결과 객체가 있어도 값은 0일 수 있다.
4. 직전 레벨로 이번 획득 점수를 계산하여 정확히 한 번 더한다.
5. 누적 삭제 줄 수를 갱신하고 다음 동작에 적용할 레벨·주기를 계산한다.
6. 코어 phase를 반영한다. 게임오버를 만든 마지막 동작의 점수도 먼저 반영하고 결과를 확정한다.

예: 누적 9줄·레벨 0에서 하드드롭으로 3칸 하강하고 1줄을 지우면 3+100점을 얻는다. 이후 누적 10줄·레벨 1·900ms 주기가 적용된다.

정지·재개는 코어에도 PAUSE/RESUME을 전달하여 세션과 엔진 상태를 맞춘다. ABORTED는 세션 상태이며 이후 엔진 호출을 차단한다.

## 6. 설정 API

```java
var settings = new SettingsService(new PropertiesSettingsRepository(settingsPath));
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

| 명령 | 기본 키 식별자 |
| --- | --- |
| MOVE_LEFT / MOVE_RIGHT | LEFT / RIGHT |
| SOFT_DROP / ROTATE_CW | DOWN / UP |
| HARD_DROP | SPACE |
| PAUSE / RESUME | P |
| QUIT_GAME | ESCAPE |

모든 `GameCommand`의 키 지정 필수. 식별자는 `[A-Z][A-Z0-9_]*` 형식으로 제한.
허용되는 실제 키 목록과 이벤트 변환은 UI와 추가 확인 필요.
RUNNING 명령끼리의 키 충돌 및 PAUSED에서 RESUME과 QUIT_GAME의 충돌 거부.
PAUSE와 RESUME은 문맥이 달라 같은 키 사용 가능.
키 매핑은 방어 복사 후 수정 불가능한 형태로 제공.

## 7. 기록 API

```java
var scores = new ScoreboardService(new BinaryScoreRepository(scoresPath));
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

## 8. 오류와 임시 실행

모델·이름 검증 실패는 `IllegalArgumentException`, 필수 인자의 null은 `NullPointerException`.
저장소 실패는 경로와 원인을 포함하는 `StorageException`으로 전달.

| `StorageException.Kind` | 의미 | UI 처리 예시 |
| --- | --- | --- |
| READ_FAILED | 파일 읽기 실패 | 오류 안내 후 재시도 또는 임시값 사용 |
| INVALID_DATA | 파일 형식·필수 필드·모델 무결성 오류 | 원본 보존 및 복구 안내 |
| UNSUPPORTED_VERSION | 지원하지 않는 schemaVersion | 버전 확인 안내 |
| WRITE_FAILED | 임시 파일 작성 또는 교체 실패 | 기존 화면값 유지 및 재시도 |

`loadOrDefault()`와 `loadOrEmpty()`의 `error`를 확인하여 임시값 사용 사실을 표시할 책임은 UI에 있음.
임시값 반환 자체는 저장 성공이나 원본 복구를 의미하지 않음.
손상 파일은 읽기만으로 덮어쓰거나 백업·삭제하지 않음.
사용자가 명시적으로 설정 저장·초기화 또는 순위 초기화를 요청하면 해당 파일 교체 가능.
손상된 순위 파일에 대한 일반 기록 등록은 읽기 오류로 중단. 임시 빈 목록에 자동 덮어쓰기 없음.

서비스는 저장 실패한 변경을 캐시하지 않으며 원본 `GameResult`를 변경하지 않음.
종료 조율자 또는 UI에서 결과를 보존하면 같은 결과로 재시도 가능.

## 9. 종료 조율자

`EndGameCoordinator.begin(GameResult)`와 `submitName(UUID gameId, String name)`은 `EndGameView` 반환.
결과는 게임별로 보관하므로 이름 입력 또는 저장 실패 후 같은 게임 ID로 재시도 가능.

| stage | 의미 | UI의 다음 동작 |
| --- | --- | --- |
| CHECKING | 순위 조회 실패로 등록 대상 판단 미완료 | 오류 표시 후 `begin(result)` 재호출 |
| NAME_REQUIRED | 이름 입력 필요 | 입력 후 `submitName(gameId, name)` 호출 |
| SHOW_SCOREBOARD | 등록 또는 비대상 판단 완료 | scores 표시, highlightedRecordId가 있으면 강조 |
| RETURN_MENU | 중도 종료 | 이름 입력 없이 메뉴 복귀 또는 프로그램 종료 |

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

## 10. 실행 조립부

`AppComposition(Path dataDirectory)`는 다음 객체 연결.

- `PropertiesSettingsRepository(dataDirectory/settings.properties)` → SettingsService
- `BinaryScoreRepository(dataDirectory/scores.bin)` → ScoreboardService → EndGameCoordinator
- 새 게임마다 SevenBagGenerator → PlayerEngine(10, 20) → SinglePlayerSession
- 기본 ScorePolicy·SpeedPolicy 주입

`newGame()`은 `StartedGame(session, settings)` 반환.
`settings`는 `LoadResult<Settings>`이며 매 게임 시작 시 최신 저장 설정 조회.
읽기 실패 시 기본 설정으로 게임 생성과 함께 오류 반환. UI에서 오류 안내 필요.
설정의 표시 크기는 보드 논리 크기 10×20에 영향 없음.
이전 판의 세션과 설정 값은 새 게임 생성으로 변경되지 않음.

생성기 팩터리와 점수·속도 규칙을 받는 생성자도 제공.
생성기 팩터리는 새 게임마다 독립적인 인스턴스 반환 필요.
세션·서비스·종료 조율자 호출은 하나의 게임 루프 실행 흐름으로 직렬화 필요.

## 11. UI 연결

UI는 `GameUi.open(ApplicationContext)` 구현.
`ApplicationContext`로 settings(), scores(), endings(), newGame() 접근 가능.
UI에서 구체 저장소나 PlayerEngine을 생성할 필요 없음.

직접 연결 예시:

```java
TetrisApplication.launch(dataDirectory, application -> {
    // 메뉴 구성 후 새 게임 선택 시 호출.
    StartedGame started = application.newGame();
    GameSession session = started.session();
    // UI 루프에서 update → handle → snapshot 순서로 진행.
    // result가 있으면 application.endings().begin(result)로 종료 흐름 시작.
});
```

표준 `main`은 Java ServiceLoader로 GameUi 구현을 검색.
팀원 1의 public 기본 생성자를 가진 구현 클래스 이름을 다음 리소스에 한 줄로 등록 필요.

```text
src/main/resources/META-INF/services/team.tetris.application.port.GameUi
```

제공자가 없으면 연결 방법을 포함한 오류로 종료. 빈 화면을 실행하거나 게임 실행 성공으로 처리하지 않음.
현재 등록된 제공자는 **테스트 리소스의 가짜 UI뿐**이며 배포 JAR에는 미포함.
실제 UI 제공자 등록 이후 사용 가능한 실행 명령:

```sh
./gradlew run
./gradlew run --args='--data-dir /path/to/data'
```

기본 데이터 디렉터리는 사용자 홈의 `.se-tetris-team13`.
`--data-dir PATH`로 명시적 변경 가능. 조립과 조회만으로 파일 생성 없음.
실제 저장 시 디렉터리 생성 및 쓰기 실패는 기존 저장 오류 계약으로 전달.
OS별 표준 데이터 경로와 아이콘 포함 배포 패키지는 후속 협의·검증 대상.
Gradle application 플러그인으로 실행 스크립트와 zip/tar 배포물 생성. 실제 UI 없는 현재 배포물은 플레이 가능한 완성 앱이 아님.

## 12. 협의 및 확인 사항

| 대상 | 확인·결정할 내용 |
| --- | --- |
| 팀원 1 | 단일 스레드 호출, 경과 시간 전달, 입력 반복, 스냅샷 표시의 UI 제약 |
| 팀원 1 | 화면 크기 실제 값, 허용 키 목록·이벤트 변환, 색맹 모드 표현, 메뉴 선택 키 |
| 팀원 1 | GameUi 구현·등록, 종료 상태별 화면 전환, 오류 안내·재시도, 이름 입력 취소 |
| 팀원 2 | 코어 결과 API 변경 공유와 application의 점수·속도 정책 위치 확인 |
| 팀원 2/전체 | SevenBagGenerator의 7-bag과 Req1 p.9 동일 확률 문구 해석. 기존 계획의 독립 1/7 추첨 제안 검토 |
| 팀 전체 | 점수·속도 수치, 최대 5회 따라잡기, 블록 고정·정지 시 타이머 초기화 기준안 수용 |
| 팀 전체 | 순위 보관 개수, 동점·이름 규칙, 중도 종료 기록 제외, 등록 이력 정리 정책 |
| 팀 전체 | 운영 데이터 경로, 대상 OS의 원자적 파일 교체 지원, 복구 안내 방식 |
| 팀 전체 | 공용 Gradle·CI 담당, 배포 OS·패키지 담당, 커버리지·성능 검증 기준 |

[아키텍처 결정 기록](architecture-decision.md)의 역할 표에는 점수·속도가 팀원 2로 남아 있으므로 최신 역할 합의와 동기화 필요.

합의 결과 기록:

| 항목 | 결정 내용 | 담당 | 합의일 | 코드·문서 반영 |
| --- | --- | --- | --- | --- |
| 미정 | — | — | — | 협의 대기 |

