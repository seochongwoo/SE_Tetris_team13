# P2P 통신 계약 (카드 22)

2026-11-12, 이슈 #49. 안건 ⑳의 **상태 전송**을 구체화한다. 이 문서는 카드 22 구현의 계약이며, 상대방 리뷰·머지 전이다. 로비 화면·최근 IP는 카드 23, 엔진/화면 연결과 결과 판정은 카드 24, 무응답 판정·랙 표시는 카드 25에서 구현한다.

## 1. 포트와 연결 수명

- application의 `MatchTransport`를 `network.SocketMatchTransport`가 구현한다. TCP 연결 하나에 서버/클라이언트 각 한 명이다. `core`와 기존 1인 게임에는 의존성을 추가하지 않는다.
- `listen(port)` / `connect(host, port)`는 즉시 반환하고 작업 스레드에서 연결한다. `status()`로 `NEW → LISTENING 또는 CONNECTING → HANDSHAKING → CONNECTED`를 확인한다. 서버의 `localPort`가 0이 아닌 때 bind가 끝났다. 실패 시 `FAILED`와 사유, 명시적 `close()` 시 `CLOSED`가 된다.
- 실제 서비스의 고정 포트 숫자는 카드 23의 조립에서 지정한다. 테스트는 `listen(0)`으로 OS가 빈 포트를 배정하게 한다. 클라이언트는 1~65535 포트를 사용한다.
- 접속과 핸드셰이크 읽기 타임아웃은 각각 3초다. 정상 연결 후에는 읽기 타임아웃으로 경기 종료를 판단하지 않는다. 카드 25에서 3초 무응답 정책을 적용한다.
- 송신도 유한 큐(256개)에 넣고 즉시 반환한다. 별도 writer가 FIFO로 전송한다. 수신 큐도 256개이며, 어느 큐든 넘치면 메시지를 조용히 버리지 않고 연결을 실패 처리한다. 한 프레임은 최대 16KiB다.
- `close()`는 대기 중인 accept/connect와 연결 소켓을 닫고 작업을 중단하며 큐를 비운다. 실패도 같은 자원 정리를 수행한다. 종료된 객체를 재사용하지 않고 새 어댑터로 재접속한다. 연결을 유지하는 재대전은 같은 어댑터를 사용한다.
- 종료 시 미전송 메시지는 보장하지 않는다. 정상 경기 종료는 `RESULT`를 전송하고 연결을 유지한다. `LEAVE`에 따른 결과 교환을 마치기 전에 `close()`하지 않는다. 갑작스러운 종료/EOF는 끊김으로 처리한다.

## 2. 호환성과 프레임 형식

카드 22의 호환성 정책은 **동일한 프로토콜 버전 허용**이다. 앱 빌드 문자열은 비교하지 않는다. 현재 `MatchMessage.PROTOCOL_VERSION = 1`이며 메시지 필드·의미·상태 계약을 비호환으로 바꾸면 이 번호를 올린다. 양쪽은 접속 직후 `HELLO(version, role)`를 전송한다. 상대가 반대 역할이며 버전이 같아야 `CONNECTED`가 된다. HELLO 이전의 게임 메시지, 재차 HELLO, 버전/역할 불일치는 연결 실패다.

Java 객체 직렬화 대신 다음 순서의 binary frame을 사용한다. 구현은 `MessageCodec`이다.

1. 프레임 본문 길이: signed int32, 1~16384. 길이 필드 자체는 제외.
2. magic int32 `0x54455433` (`TET3`).
3. `matchId` int64 (0 이상), `sequence` int64 (1 이상).
4. 메시지 타입 문자열, 이어서 아래 표 순서의 payload.

정수는 big-endian, boolean은 1 byte의 0/1만 허용한다. 문자열은 `DataOutputStream.writeUTF` 형식(2 byte 길이 + modified UTF-8)이며 현재 타입/enum 토큰은 모두 ASCII다. enum은 ordinal 대신 이름을 보낸다. 배열은 int32 개수 후 원소를 순서대로 보낸다. 보드만 크기가 고정되어 개수를 생략한다. 알 수 없는 타입·enum, 비정상 길이·필드·boolean, 남는 바이트, 잘린 프레임은 연결 실패다.

## 3. 메시지 필드와 허용 상태

역할은 `SERVER` / `CLIENT`. 경기 상태는 `LOBBY` / `PLAYING` / `PAUSED` / `RESULT`다. 연결과 heartbeat는 어댑터가, 아래의 경기 상태 검증은 게임 실행 스레드의 `MatchProtocol`이 담당한다.

| 타입 | payload (전송 순서) | 송신자 / 허용 상태 / 의미 |
| --- | --- | --- |
| HELLO | version int32, role 문자열 | 양쪽 / HANDSHAKING에서 최초 한 번, matchId=0 |
| LOBBY | revision int64, mode 문자열, difficulty 문자열, timeLimitSeconds int32 | 서버 / LOBBY·RESULT. 새 revision은 이전보다 커야 하며 양쪽 준비를 해제. RESULT에서는 로비로 복귀 |
| READY | revision int64, ready boolean | 양쪽 / LOBBY. 현재 설정 revision에 대한 준비 또는 해제 |
| START | revision int64, pieceSeed int64 | 서버 / LOBBY. 현재 설정에 양쪽 준비 완료, 이전보다 큰 matchId 필요. PLAYING으로 전환 |
| STATE | player (아래 형식), remainingNanos int64 | 양쪽 / PLAYING·PAUSED. 송신자 자신의 화면·논리적 패배. 서버는 남은 시간 0 이상(시간제한 외 모드는 0), 클라이언트는 -1 |
| ATTACK | eventId int64, rows (int32 배열) | 양쪽 / PLAYING·PAUSED. eventId는 경기·송신자별 증가. 줄 모양만 전송 |
| PAUSE | owner 문자열, request boolean | request=true: 양쪽 중 본인이 owner인 정지 요청 / PLAYING. false: 서버 확정, PAUSED로 전환하고 재개 권한자를 기록 |
| RESUME | request boolean | request=true: PAUSED에서 정지 owner만 요청. false: 서버 확정, PLAYING으로 전환 |
| RESULT | outcome 문자열, reason 문자열 | 서버 / PLAYING·PAUSED. RESULT로 전환. 이후 상태·공격·정지·재개 메시지 적용 금지 |
| LEAVE | 없음 | 양쪽 / 로비 및 경기·결과. 송신자가 나감을 통지하고 해당 준비를 해제. 경기 중/정지 중 상대 승리 확정은 카드 24의 서버가 RESULT로 보냄 |
| PING | id int64 | 양쪽 / CONNECTED 수명 전체, matchId=0. 어댑터가 1초마다 전송 |
| PONG | id int64 | 상대 PING의 id를 그대로 응답, matchId=0. 어댑터에서 소비 |

- mode: `NORMAL`, `ITEM`, `TIMED`. difficulty: `EASY`, `NORMAL`, `HARD`.
- TIMED는 60/120/180초, 나머지 모드는 0초다. revision/eventId/ping id는 양수. 기본 모드·시간 선택은 카드 23의 책임이다.
- outcome: `SERVER_WINS`, `CLIENT_WINS`, `DRAW`. reason: `TOP_OUT`, `TIME_LIMIT`, `LEFT`.
- `pieceSeed`는 기본 테트로미노 순서 공유용이다. 아이템 공급·난수와 지급 시점은 플레이어별로 분리한다(안건 ⑰).
- 정지 요청 자체는 상태를 바꾸지 않는다. 서버 확정 시점에 양쪽 세션·타이머를 정지한다. PAUSED에 도착하는 STATE/ATTACK은 정지 확정 전 진행에서 생긴 전송 중 메시지일 수 있어 수신을 허용한다. 카드 24는 공격을 대기열에만 보관하고, 재개 후 블록 고정 경계에서 적용한다. 정지 중 엔진 진행·점수 증가를 허용한다는 뜻이 아니다.
- RESULT 뒤 다음 LOBBY는 더 큰 revision을 사용한다. 카드 24가 결과를 표시한 뒤 서버가 LOBBY를 전송하며, 양쪽 준비는 다시 시작한다.

### player 형식

화면 값 `PlayerState`는 불변 리스트이며 엔진 객체·Shape 구현·Item 인스턴스를 포함하지 않는다.

1. `board`: 10×20의 Tile 200개, 위→아래/왼쪽→오른쪽 순서.
2. `active`: PlacedTile 배열(0~6개), 보드 절대 좌표. 블록이 없으면 빈 배열.
3. `next`: PlacedTile 배열(1~6개), 미리보기 상대 좌표. 무게추는 위 2칸+아래 4칸, 총 6칸.
4. `score` int64, `level` int32, `clearedLines` int32, `doubleScoreClears` int32.
5. `defeated` boolean: 삭제 연출과 분리된 논리적 패배. 최종 승패는 서버 RESULT만 확정한다.
6. `pendingRows`: 공격 행 마스크 배열(0~10개).

Tile은 shape 문자열 다음 item 문자열이다. shape는 `EMPTY/I/O/T/S/Z/J/L/WEIGHT/SOLO/ATTACK/TEMPORARY`, item은 `NONE/LINE_CLEAR/BOMB/CHAIN_CLEAR/DOUBLE_SCORE/WEIGHT`. EMPTY에는 NONE만 가능하다. PlacedTile은 x int32, y int32, Tile 순서이며 x=0~9, y=-4~19, EMPTY는 금지한다. 보드 밖 위쪽 좌표는 스폰 영역이다. 점수·삭제 줄 수·2배 횟수는 0 이상, 레벨은 1 이상이다.

보드·블록은 화면 투영용이다. 카드 24에서 스냅샷을 이 형식으로 변환하며, 삭제 연출 중에는 표시할 칸을 구성해서 보낸다. 원격 엔진을 이 값으로 다시 시뮬레이션하지 않는다.

공격 행은 bit x가 x열의 칸인 10-bit 마스크다. 기존 빈칸을 유지하고 아이템은 제거한다. 0~1022만 허용하여 꽉 찬 행은 거부한다. 위에서 아래 순서를 보존한다. ATTACK은 2~10행이며, 10행보다 긴 공격은 송신 시 위쪽 10행까지만 보낸다. 수신 측 대기열의 총 10행 제한·아래 잘라내기는 기존 `AttackQueue`가 담당한다.

## 4. 경기 번호·순서·중복

- LOBBY/READY 및 transport 제어 메시지는 matchId=0. START는 이전 경기보다 큰 양수. STATE/ATTACK/PAUSE/RESUME/RESULT는 현재 경기 번호. LEAVE는 로비에서 0, 그 밖에는 현재 경기 번호다.
- sequence는 **application 메시지의 송신자별 연결 전체 순번**이다. 1에서 시작하고 재대전에도 리셋하지 않는다. 로컬 발신 전과 원격 수신 후 동일한 `MatchProtocol.accept(sender, envelope)`를 호출한다. sender는 연결 역할에서 결정하며 payload로 임의 지정하지 않는다.
- HELLO/PING/PONG은 어댑터 전용이다. HELLO의 sequence는 1, heartbeat는 id를 sequence에도 사용한다. application의 순번 검사와 별개이며 `poll()`로 나오지 않는다.
- 이미 승인한 sequence 이하의 메시지 및 현재 경기에서 이미 승인한 eventId 이하의 ATTACK은 `DUPLICATE`. 다른 경기 메시지는 `STALE_MATCH`. 틀린 상태/권한/설정은 각각 `WRONG_STATE` / `WRONG_SENDER` / `STALE_CONFIGURATION`이다. 거부 시 게임과 검사기 상태는 바뀌지 않는다.
- 공격 eventId는 `(matchId, sender, eventId)`로 구분한다. 새 START 때 공격 ID 기록을 초기화한다. 같은 공격에 새 sequence를 붙여 다시 보내도 재적용하지 않는다. TCP 연결 중 재전송은 필요하지 않다.
- 송수신 큐는 FIFO다. 최신 STATE만 남기려고 큐에서 메시지를 건너뛰지 않는다. 상태와 공격 사이의 순서를 보존한다.

## 5. 게임 실행과 UI 반영 경로

현재 게임 실행은 Swing `GameLoop`의 EDT다. 카드 23·24에서 매 프레임 다음 순서로 연결한다.

1. `transport.status()`를 확인한다. FAILED/CLOSED이면 큐 적용을 중단하고 종료 화면 처리를 한다.
2. `poll()`로 이번 프레임 예산 이내의 메시지를 FIFO로 가져온다. 수신 작업 스레드는 DTO를 큐에 넣을 뿐 세션·검사기·Swing 콜백을 호출하지 않는다.
3. `protocol.accept(remoteRole, envelope)`가 ACCEPTED일 때만 로비/대전 컨트롤러에 적용한다. 거부 결과는 진단용으로 기록하고 적용하지 않는다.
4. 로컬 입력/시간 진행과 서버의 승패 확정을 같은 EDT에서 처리한다. 로컬 발신도 이 스레드에서 새 sequence를 부여하고 `accept(localRole, envelope)` 검증 후 `send(envelope)`한다. 송신 큐 초과는 status=FAILED로 보고 다음 프레임에서 중단한다.
5. 컨트롤러가 만든 불변 화면 스냅샷을 기존 렌더링 경로에 넘겨 EDT에서 그린다. RESULT 이후 엔진 입력·점수·공격을 막는 것은 카드 24의 세션 책임이다.

소켓에서 바이트가 도착하는 것만으로 `MatchProtocol`이나 엔진이 바뀌지 않는다는 경계를 통합 테스트로 확인한다. 이 카드에서는 기존 메뉴·게임 루프에 P2P를 연결하지 않는다.

## 6. RTT와 검증 범위

PING을 실제 writer가 쓰기 직전에 로컬 `System.nanoTime()`을 저장하고 같은 id의 PONG 수신 때 차이를 잰다. 최대 네 개의 미응답 id만 보관하며, 모르는 id·만료 id·중복 PONG은 측정값을 바꾸지 않는다. 상대 시계는 사용하지 않는다. `roundTripNanos()`는 첫 응답 전 empty, 이후 최근 유효 RTT다.

이 RTT는 **입력→상대 화면 표시 200ms(N3-01)의 증거가 아니다**. 렌더링 확인 응답과 실제 PC 두 대를 사용하는 [확정된 측정 절차](open-questions.md#n3-01-측정-절차-2026-11-07-확정)는 카드 24·28에서 수행한다. 200ms 초과 랙 표시/회복과 3초 무응답 종료는 카드 25 범위다.

| 검증 대상 | 자동화 증거 |
| --- | --- |
| 전체 메시지 왕복, 무게추·아이템·공격 칸, 부분 읽기/연속 프레임, 잘못된 프레임 거부 | `MessageCodecTest` |
| 준비/설정 변경, 서버 권한, 정지/재개, 결과 후 차단, 두 번째 경기, 이전 경기·중복 공격 거부 | `MatchProtocolTest`, `SocketMatchTransportTest` |
| localhost 접속·양방향 순서 보존·비호환 거부·비정상 핸드셰이크·타임아웃·접속 실패·종료/재접속 | `SocketMatchTransportTest` |
| 네트워크 수신 후에도 게임 스레드가 처리하기 전까지 경기 상태 유지 | `SocketMatchTransportTest.socketReceptionDoesNotAdvanceProtocolUntilGameThreadDrainsAndValidates` |
| 로컬 단조 시계 기반 RTT, 중복/미응답 id 처리 | `PingTrackerTest`, `SocketMatchTransportTest` |

기존 CI의 `build jacocoTestReport`가 이 테스트를 함께 실행한다. Windows CI와 상대방 리뷰 결과는 PR에서 별도로 확인한다.

2026-11-12 로컬 검증(Linux, Java 21): `./gradlew --offline --no-daemon build jacocoTestReport` 성공. 전체 699개 테스트(통신 관련 신규 20개 포함), 실패·오류·건너뜀 0개. 라인 커버리지 4,146 / 4,337 = **95.60%**, 70% 게이트 통과. 보고서는 `build/reports/tests/test/index.html`, `build/reports/jacoco/test/html/index.html`이다. 실제 LAN·Swing 화면 지연 실측과 Windows CI는 이 결과에 포함하지 않는다.
