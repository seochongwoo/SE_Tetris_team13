package team.tetris.core.rule;

import team.tetris.core.TetrominoType;

/**
 * 다음에 등장할 블록을 결정하는 정책. 구현체는 next()로 뽑을 때마다 내부 상태를
 * 전진시키고, peek()은 상태를 바꾸지 않고 다음 블록을 미리 보여준다.
 *
 * peek() 직후에 호출한  next()는 반드시 방금 peek한 것과 같은 블록을
 * 반환해야 한다 (미리보기 화면과 실제로 나오는 블록이 달라지면 안 됨).
 */
public interface PieceGenerator {

    /**
     * 다음 블록을 뽑아 반환하고, 내부 상태를 한 칸 전진시킨다.
     */
    TetrominoType next();

    /**
     * 다음에 next()가 반환할 블록을 상태 변화 없이 미리 본다.
     * UI의 "다음 블록" 미리보기 표시에 사용된다.
     */
    TetrominoType peek();
}
