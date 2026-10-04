package team.tetris.core.rule;

import team.tetris.core.Position;

/**
 * RotationSystem/tryRotate의 결과.
 *
 * code rotated가 false이면 회전에 실패한 것이고, 이때 code rotation/code origin은
 * 시도 전 상태 그대로 담겨 있다 - 호출하는 쪽(PlayerEngine)이 성공/실패를 따로 분기하지 않고
 * 항상 이 결과의 rotation/origin을 그대로 적용해도 안전하게 만들기 위함이다.
 */
public record RotationResult(boolean rotated, int rotation, Position origin) {

    public static RotationResult success(int rotation, Position origin) {
        return new RotationResult(true, rotation, origin);
    }

    public static RotationResult failed(int rotation, Position origin) {
        return new RotationResult(false, rotation, origin);
    }
}
