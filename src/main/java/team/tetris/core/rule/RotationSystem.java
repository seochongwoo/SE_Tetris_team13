package team.tetris.core.rule;

import team.tetris.core.Board;
import team.tetris.core.Position;
import team.tetris.core.TetrominoType;

/**
 * 회전 시도와 벽/바닥 근처에서의 간단한 밀어내기(벽차기)를 담당한다.
 *
 * 먼저 제자리 회전을 시도하고, 막히면 ICK_OFFSETS에 정의된 offset들을 순서대로
 * 시도해 그 중 처음으로 놓을 수 있는 위치를 채택한다. 표준 SRS의 피스별/전이별 벽차기 표를
 * 그대로 구현한 것은 아니고, 대부분의 벽/바닥 근접 상황을 커버하는 단순화된 오프셋 집합이다 -
 * 요구사항은 "시계방향 90도 회전"만 요구하므로 이 정도로 충분하고, 필요해지면 나중에 피스별
 * 전용 표로 교체할 수 있다.
 *
 * 회전 결과의 칸이 하나라도 보드 위쪽(y < 0)으로 나가는 후보는 건너뛴다. 스폰 직후 회전해도
 * 블록이 화면에서 잘려 보이지 않게 하기 위함이다.
 */
public final class RotationSystem {

    private static final Position[] KICK_OFFSETS = {
            new Position(0, 0),
            new Position(-1, 0),
            new Position(1, 0),
            new Position(-2, 0),
            new Position(2, 0),
            new Position(0, -1),
            new Position(-1, -1),
            new Position(1, -1),
    };

    /** 회전 상태 개수와 동일 (0~3). TetrominoType/ROTATION_STATES과 일치시켜 둔다. */
    private static final int ROTATION_STATES = TetrominoType.ROTATION_STATES;

    /**
     * type을 currentRotation/currentOrigin 상태에서 clockwise 방향으로 90도 회전을 시도한다.
     * 성공하면 새 rotation/origin을 담아 반환하고, 모든 벽차기 시도가 실패하면 원래 상태를
     * 그대로 담아 실패를 반환한다.
     */
    public RotationResult tryRotate(
            Board board, TetrominoType type, int currentRotation, Position currentOrigin, boolean clockwise) {
        int targetRotation = Math.floorMod(currentRotation + (clockwise ? 1 : -1), ROTATION_STATES);

        for (Position kick : KICK_OFFSETS) {
            Position candidateOrigin = currentOrigin.translate(kick.x(), kick.y());
            if (isFullyOnBoard(type, targetRotation, candidateOrigin)
                    && board.canPlace(type, targetRotation, candidateOrigin)) {
                return RotationResult.success(targetRotation, candidateOrigin);
            }
        }

        return RotationResult.failed(currentRotation, currentOrigin);
    }

    /** 회전 결과의 모든 칸이 보드 위쪽 경계 안(y >= 0)에 있어야 한다 - 칸이 화면 밖으로 잘려 나가지 않게 한다. */
    private static boolean isFullyOnBoard(TetrominoType type, int rotation, Position origin) {
        for (Position offset : type.cellsAt(rotation)) {
            if (origin.y() + offset.y() < 0) {
                return false;
            }
        }
        return true;
    }
}
