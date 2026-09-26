# 설정·기록·저장소 구현 계약

작성일: 2026-09-26

설정과 기록을 저장하는 서비스·메모리 저장소·파일 저장소 구현 완료.
UI 화면 연결, `EndGameCoordinator`, 실행 조립과 배포 경로 선정은 후속 작업.
기본값과 순위 수치는 현재 작업 계획의 구현 기준안이며 팀 전체 합의와 별개.

## 1. 설정 API

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

## 2. 기록 API

```java
var scores = new ScoreboardService(new BinaryScoreRepository(scoresPath));
if (scores.qualifies(result)) {
    // UI에서 이름 입력 후 등록 시점의 순위 재판단.
    Optional<UUID> recordId = scores.register(result, name);
}
List<ScoreEntry> entries = scores.list();
```

- `list()`: 점수 내림차순의 수정 불가능한 목록 반환.
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

## 3. 오류와 임시 실행

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

## 4. 파일 형식과 교체

외부 JSON 라이브러리 없이 Java 기본 API만 사용. 설정은 Properties, 기록은 데이터 스트림 바이너리로 저장.
권장 파일명은 `settings.properties`, `scores.bin`. 이전 JSON 파일의 자동 변환은 제공하지 않으며 새 경로 사용 필요.
기존 JSON 파일을 새 저장소에 전달하면 INVALID_DATA로 보고하고 읽기 중 원본 유지.

### 설정 파일

UTF-8 Properties 형식. `schemaVersion=1`, `screenSize`, `colorBlindMode`, `key.<명령>` 필수.
중복·누락·알 수 없는 필드, 잘못된 UTF-8, boolean 이외의 값, 잘못된 프리셋·키 매핑 거부.
`Properties.store(Writer, ...)`로 생성하므로 주석에 저장 시각 포함.

### 기록 파일

`DataOutputStream` / `DataInputStream`의 big-endian 형식 사용.

1. 파일 식별자 `0x54545343`(int), 버전 1(int), 기록 개수(int)
2. 각 기록: recordId(UUID long 두 개), gameId(UUID long 두 개), 이름(writeUTF), 점수(long), 등록 시각의 epochSecond(long)와 nano(int)
3. 등록 이력 개수(int), 게임 UUID와 기록 UUID 쌍

목록 순서 유지, long 최대 점수와 나노초 정밀도의 등록 시각 복원.
이름은 Java modified UTF-8 형식으로 저장하며 인코딩 길이 65,535바이트 제한 적용.
기본 이름 제한 12 코드 포인트는 이 범위 안에 해당. 사용자 정의 정책에서 제한을 넘는 이름은 WRITE_FAILED로 보고.
잘린 파일, 잘못된 식별자·개수·시각, 불필요한 후행 데이터, 중복 게임·누락된 등록 이력 거부.

파일 경로는 각 저장소 생성자에 주입. 서로 다른 설정·기록 경로 사용 필수.
저장 시 상위 디렉터리 생성 → 같은 디렉터리에 임시 파일 작성 → 파일 데이터 동기화 → 원자적 교체 순서.
원자적 교체가 지원되지 않으면 `WRITE_FAILED`로 처리하고 기존 파일 유지.
비원자적 덮어쓰기로 자동 전환하지 않음. 파일 시스템·대상 OS에서 지원 여부 검증 필요.
실패 시 임시 파일 정리 시도. 프로세스 강제 종료로 남은 임시 파일은 로드 대상에서 제외.
정전 시 디렉터리 항목까지의 영속성이나 여러 프로세스의 동시 쓰기는 보장 범위 밖.
서비스·저장소는 하나의 실행 흐름에서 호출하는 계약.

## 5. 검증

`./gradlew --offline --no-daemon clean build jacocoTestReport` 로컬 통과.
전체 테스트 132개 통과, 실패·오류·건너뜀 0.

- 메모리 저장소: 설정 검증·주입·초기화, 순위 경계·동점·정책 교체, 중복 등록, 저장 실패·재시도
- 임시 디렉터리: 새 인스턴스 재로드, 한국어·이모지·long 점수·시각 복원, 초기화 후 재로드
- 오류 경계: 파일 부재·손상·잘린 바이너리·미지원 버전·읽기 실패·쓰기 실패·원자적 교체 미지원
- 보존성: 읽기 실패 시 원본 유지, 교체 실패 시 기존 바이트 유지, 설정과 순위 초기화 독립성

실제 사용자 데이터 경로에는 쓰지 않았으며 화면·실행 조립 및 다른 OS 검증은 후속 범위.
