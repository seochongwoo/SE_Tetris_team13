package team.tetris.core;

/**
 * 블록 모양 데이터. 회전 상태별로 차지하는 칸의 상대 좌표를 제공한다.
 *
 * <p>일반 블록 7종({@link TetrominoType})뿐 아니라 무게추처럼 테트로미노가 아닌 블록도 이
 * 인터페이스로 표현한다. 보드 배치, 충돌 판정, 회전은 모두 이 인터페이스만 보고 동작하므로,
 * 새로운 모양의 블록은 구현체를 추가하는 것으로 끝난다.
 *
 * <p>구현체는 회전해도 같은 인덱스가 같은 칸을 가리키도록 좌표를 정렬해야 한다. 블록의 한 칸에
 * 붙은 아이템({@link Piece#items()})이 칸 인덱스로 추적되기 때문이다.
 */
public interface Shape {

    /**
     * rotation 상태에서 차지하는 칸들의 상대 좌표. 어떤 정수든 받아 회전 상태 수로 정규화한다.
     * 반환 배열은 복사본이다.
     */
    Position[] cellsAt(int rotation);

    /** 회전 상태 수. 1이면 회전하지 않는 블록이다. */
    int rotationStates();

    /** 블록을 이루는 칸 수. */
    default int cellCount() {
        return cellsAt(0).length;
    }
}
