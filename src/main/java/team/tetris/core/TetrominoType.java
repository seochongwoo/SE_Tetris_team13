package team.tetris.core;

/**
 * 7종류 테트로미노와, 회전 상태(0~3)별로 차지하는 4칸의 상대 좌표 테이블.
 *
 * <p>좌표는 블록의 origin을 기준으로 한 상대값이며, 실제 보드 좌표로 바꾸려면
 * {@code origin.translate(offset.x(), offset.y())}를 사용한다. 각 타입의 회전상태 0(스폰
 * 모양)은 요구사항이 지정한 참조 코드
 * (<a href="https://github.com/Jindae/SeoulTech-SE-Tetris-Ref">SeoulTech-SE-Tetris-Ref</a>)의
 * 블록 정의와 셀 단위로 일치하도록 맞췄고, 나머지 회전상태는 거기서 시계방향 90도씩 돌린 값이다.
 * 벽차기(wall kick) 오프셋은 여기 포함되지 않고 {@code core.rule.RotationSystem}에서 별도로 다룬다.
 */
public enum TetrominoType {
    I(new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
            {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
            {{1, 0}, {1, 1}, {1, 2}, {1, 3}},
    }),
    O(new int[][][] {
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
    }),
    T(new int[][][] {
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {1, 1}, {2, 1}, {1, 2}},
            {{0, 1}, {1, 1}, {2, 1}, {1, 2}},
            {{1, 0}, {0, 1}, {1, 1}, {1, 2}},
    }),
    S(new int[][][] {
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
            {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
            {{1, 1}, {2, 1}, {0, 2}, {1, 2}},
            {{0, 0}, {0, 1}, {1, 1}, {1, 2}},
    }),
    Z(new int[][][] {
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
            {{2, 0}, {1, 1}, {2, 1}, {1, 2}},
            {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
            {{1, 0}, {0, 1}, {1, 1}, {0, 2}},
    }),
    J(new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
            {{1, 0}, {1, 1}, {0, 2}, {1, 2}},
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {1, 2}},
    }),
    L(new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {0, 2}},
            {{0, 0}, {1, 0}, {1, 1}, {1, 2}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
    });

    /** 회전 상태 개수 (0~3). */
    public static final int ROTATION_STATES = 4;

    private final Position[][] cellsByRotation;

    TetrominoType(int[][][] rawOffsets) {
        this.cellsByRotation = new Position[rawOffsets.length][];
        for (int rotation = 0; rotation < rawOffsets.length; rotation++) {
            int[][] offsets = rawOffsets[rotation];
            Position[] cells = new Position[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                cells[i] = new Position(offsets[i][0], offsets[i][1]);
            }
            this.cellsByRotation[rotation] = cells;
        }
    }

    /**
     * 주어진 회전 상태에서 이 블록이 차지하는 4개의 상대 좌표를 반환한다.
     * rotation은 음수를 포함해 어떤 정수든 받아 4로 정규화한다.
     */
    public Position[] cellsAt(int rotation) {
        int normalized = Math.floorMod(rotation, ROTATION_STATES);
        return cellsByRotation[normalized].clone();
    }
}
