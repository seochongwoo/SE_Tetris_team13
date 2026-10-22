package team.tetris.core;

/** 디버그 보드를 채우는 전용 고정 칸. 일반 블록 추첨에는 포함하지 않는다. */
public enum TemporaryBlock implements Shape {
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
        return "TemporaryBlock";
    }
}
