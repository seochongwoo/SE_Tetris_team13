package team.tetris.core.result;

import team.tetris.core.Position;
import team.tetris.core.TetrominoType;

/**
 * 이번 액션/틱으로 블록이 보드에 고정(lock)되었을 때, 그 블록이 무엇이었고 어디에
 * (회전상태 포함) 고정됐는지를 담는다. 고정이 일어나지 않았으면 이 레코드는 만들어지지 않고
 * {@link EngineStep#lockResult()}가 null이다.
 */
public record LockResult(TetrominoType type, Position origin, int rotation) {
}
