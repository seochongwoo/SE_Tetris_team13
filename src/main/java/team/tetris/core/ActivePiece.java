package team.tetris.core;

/**
 * 현재 낙하 중인 블록의 상태 - 어떤 블록인지, 어떤 회전상태인지, 보드 위 어디(origin)에 있는지.
 */
public record ActivePiece(TetrominoType type, int rotation, Position origin) {
}
