package team.tetris.core;

/**
 * PlayerEngine의 어느 한 시점 상태를 담은 불변 스냅샷. UI/application은 이 4개 필드만으로
 * 화면을 그리는 데 필요한 걸 전부 알 수 있어야 한다.
 *
 * @param board        읽기 전용 보드 스냅샷 (Board.snapshot() 결과)
 * @param activePiece  현재 낙하 중인 블록. phase가 READY/GAME_OVER면 null일 수 있다.
 * @param nextPiece    다음에 나올 블록 (PieceSource.peek() 결과, 아이템 포함) - 미리보기 표시용
 * @param phase        현재 엔진 상태
 */
public record EngineSnapshot(Cell[][] board, ActivePiece activePiece, Piece nextPiece, EnginePhase phase) {
}
