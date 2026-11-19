package team.tetris.application.network;

import java.util.Objects;

/** matchId=0은 연결/로비, 경기 번호는 연결 동안 단조 증가. sequence는 송신자별 연결 전체 순번. */
public record MatchEnvelope(long matchId, long sequence, MatchMessage message) {
    public MatchEnvelope {
        if (matchId < 0 || sequence <= 0) throw new IllegalArgumentException("Invalid envelope counters");
        Objects.requireNonNull(message);
    }
}
