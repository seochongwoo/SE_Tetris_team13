package team.tetris.core;

/**
 * 대전에서 상대의 공격으로 보드 아래에 들어온 줄의 칸. 블록 추첨에는 포함하지 않으며, 회색으로
 * 그려 일반 블록과 구분한다. 한 칸짜리 모양이라 {@link TemporaryBlock}과 같은 방식으로 보드에 기록한다.
 */
public enum AttackBlock implements Shape {
    INSTANCE;

    @Override
    public Position[] cellsAt(int rotation) {
        return new Position[] {new Position(0, 0)};
    }

    @Override
    public int rotationStates() {
        return 1;
    }

    @Override
    public String toString() {
        return "AttackBlock";
    }
}
