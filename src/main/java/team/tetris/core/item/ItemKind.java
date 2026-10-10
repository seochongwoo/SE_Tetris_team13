package team.tetris.core.item;

import java.util.random.RandomGenerator;
import team.tetris.core.Piece;

/**
 * 아이템이 등장하는 방식 하나. 아이템 모드에서 아이템이 나올 차례가 되면 다음 일반 블록을 받아
 * 아이템 블록으로 바꾼다.
 *
 * <p>블록의 한 칸에 붙는 아이템(줄 삭제 `L` 등)은 {@link AttachedItem}을 쓰고, 블록 자체가
 * 아이템인 경우(무게추 등)는 일반 블록 대신 자기 모양의 블록을 돌려주는 구현을 만든다.
 */
public interface ItemKind {

    /** next(원래 나올 일반 블록)를 이 아이템이 실린 블록으로 바꾼다. */
    Piece toItemPiece(Piece next, RandomGenerator random);
}
