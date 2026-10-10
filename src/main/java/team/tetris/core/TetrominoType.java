package team.tetris.core;

import java.util.Arrays;
import java.util.Set;

/**
 * 7종류 테트로미노와, 회전 상태(0~3)별로 차지하는 4칸의 상대 좌표 테이블.
 *
 * <p>좌표는 블록의 origin을 기준으로 한 상대값이며, 실제 보드 좌표로 바꾸려면
 * {@code origin.translate(offset.x(), offset.y())}를 사용한다. 각 타입의 회전상태 0(스폰
 * 모양)은 요구사항이 지정한 참조 코드
 * (<a href="https://github.com/Jindae/SeoulTech-SE-Tetris-Ref">SeoulTech-SE-Tetris-Ref</a>)의
 * 블록 정의와 셀 단위로 일치하도록 맞췄고, 나머지 회전상태는 거기서 시계방향 90도씩 돌린 값이다.
 * 벽차기(wall kick) 오프셋은 여기 포함되지 않고 {@code core.rule.RotationSystem}에서 별도로 다룬다.
 *
 * <p>아래 표는 읽기 쉽게 칸을 위→아래, 왼→오른쪽 순으로 적었다. 생성할 때 회전 중심(pivot)을
 * 기준으로 이전 상태를 시계방향으로 돌려 각 상태의 칸 순서를 다시 맞추므로, 회전해도 같은
 * 인덱스가 같은 칸을 가리킨다 ({@link Shape} 계약). 돌린 결과가 표와 다르면 생성 시점에 실패한다.
 */
public enum TetrominoType implements Shape {
    // pivot은 회전 중심 좌표의 2배 값 (반 칸 중심을 정수로 표현하기 위함).
    I(3, 3, new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
            {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
            {{1, 0}, {1, 1}, {1, 2}, {1, 3}},
    }),
    O(3, 1, new int[][][] {
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
    }),
    T(2, 2, new int[][][] {
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {1, 1}, {2, 1}, {1, 2}},
            {{0, 1}, {1, 1}, {2, 1}, {1, 2}},
            {{1, 0}, {0, 1}, {1, 1}, {1, 2}},
    }),
    S(2, 2, new int[][][] {
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
            {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
            {{1, 1}, {2, 1}, {0, 2}, {1, 2}},
            {{0, 0}, {0, 1}, {1, 1}, {1, 2}},
    }),
    Z(2, 2, new int[][][] {
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
            {{2, 0}, {1, 1}, {2, 1}, {1, 2}},
            {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
            {{1, 0}, {0, 1}, {1, 1}, {0, 2}},
    }),
    J(2, 2, new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
            {{1, 0}, {1, 1}, {0, 2}, {1, 2}},
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {1, 2}},
    }),
    L(2, 2, new int[][][] {
            {{0, 1}, {1, 1}, {2, 1}, {0, 2}},
            {{0, 0}, {1, 0}, {1, 1}, {1, 2}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
    });

    /** 회전 상태 개수 (0~3). */
    public static final int ROTATION_STATES = 4;

    private final Position[][] cellsByRotation;

    TetrominoType(int doubledPivotX, int doubledPivotY, int[][][] rawOffsets) {
        this.cellsByRotation = new Position[ROTATION_STATES][];
        this.cellsByRotation[0] = toPositions(rawOffsets[0]);
        for (int rotation = 1; rotation <= ROTATION_STATES; rotation++) {
            Position[] previous = cellsByRotation[rotation - 1];
            Position[] rotated = new Position[previous.length];
            for (int i = 0; i < previous.length; i++) {
                rotated[i] = rotateClockwise(previous[i], doubledPivotX, doubledPivotY);
            }
            int target = rotation % ROTATION_STATES;
            if (!Set.of(rotated).equals(Set.of(toPositions(rawOffsets[target])))) {
                throw new IllegalStateException(name() + " rotation " + target + " is not a clockwise turn");
            }
            if (target != 0) {
                this.cellsByRotation[target] = rotated;
            } else if (!Arrays.equals(rotated, cellsByRotation[0])) {
                // 네 번 돌면 원래 순서로 돌아와야 한다 (O처럼 대칭인 블록도 칸끼리는 자리를 바꾸며 돈다).
                throw new IllegalStateException(name() + " does not return to its spawn order after 4 turns");
            }
        }
    }

    private static Position[] toPositions(int[][] offsets) {
        Position[] cells = new Position[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            cells[i] = new Position(offsets[i][0], offsets[i][1]);
        }
        return cells;
    }

    /** 2배 좌표로 주어진 중심을 기준으로 시계방향 90도 회전 (y는 아래로 증가). */
    private static Position rotateClockwise(Position cell, int doubledPivotX, int doubledPivotY) {
        int x = (doubledPivotX + doubledPivotY - 2 * cell.y()) / 2;
        int y = (doubledPivotY - doubledPivotX + 2 * cell.x()) / 2;
        return new Position(x, y);
    }

    /**
     * 주어진 회전 상태에서 이 블록이 차지하는 4개의 상대 좌표를 반환한다.
     * rotation은 음수를 포함해 어떤 정수든 받아 4로 정규화한다.
     */
    @Override
    public Position[] cellsAt(int rotation) {
        int normalized = Math.floorMod(rotation, ROTATION_STATES);
        return cellsByRotation[normalized].clone();
    }

    @Override
    public int rotationStates() {
        return ROTATION_STATES;
    }
}
