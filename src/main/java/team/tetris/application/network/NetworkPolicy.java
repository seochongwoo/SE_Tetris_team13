package team.tetris.application.network;

/** 안건 ㉑의 연결 품질 기준. 입력→화면 지연(N3-01)과는 별도다. */
public final class NetworkPolicy {
    public static final long PING_INTERVAL_NANOS = 1_000_000_000L;
    public static final long LAG_NANOS = 200_000_000L;
    public static final long DISCONNECT_NANOS = 3_000_000_000L;
    public static final long CHECK_INTERVAL_MILLIS = 50;
    public static final String NO_RESPONSE = "3초 동안 상대 응답이 없어 연결을 종료했습니다";

    private NetworkPolicy() {}
}
