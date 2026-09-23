package team.tetris.core;

/**
 * 보드 한 칸의 점유 상태.
 *
 * <p>색상 등 렌더링 정보는 담지 않는다 — 그건 UI(render) 쪽 책임이다.
 * 어떤 블록 종류가 채웠는지는{Cell/occupiedBy()에 별도로 담는다.
 */
public enum CellKind {
    EMPTY,
    OCCUPIED
}
