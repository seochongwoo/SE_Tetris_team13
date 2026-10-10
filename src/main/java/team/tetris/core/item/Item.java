package team.tetris.core.item;

import team.tetris.core.Position;

/**
 * 블록의 한 칸에 실리는 아이템. 아이템 하나는 이 인터페이스의 구현체 하나다.
 *
 * <p>블록이 고정되면 엔진이 아이템이 실린 칸마다 {@link #onLock}을 한 번 호출한다. 처리 순서는
 * 블록 고정 → 아이템 효과 → 줄 삭제 판정이며, 효과에서 지정한 줄과 꽉 찬 줄은 한 번에 지워져
 * 같은 {@code ClearResult}로 보고된다. 따라서 아이템으로 지운 줄도 기존 방식대로 점수가 계산된다.
 *
 * <p>점수 배율처럼 보드 밖에 영향을 주는 아이템은 효과를 직접 적용하지 않는다. 엔진이 발동 사실을
 * {@code EngineStep#itemActivations()}로 보고하면 application이 처리한다.
 */
public interface Item {

    /** 화면에 표시할 문자. 요구사항상 아이템마다 달라야 한다. */
    char symbol();

    /**
     * 이 아이템이 실린 칸이 보드의 position에 고정된 직후 호출된다. 호출 시점에 그 칸의 아이템
     * 표시는 이미 지워져 있다(아이템은 한 번 발동하고 소모된다).
     */
    void onLock(ItemContext context, Position position);
}
