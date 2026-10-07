package team.tetris.ui.render;

import team.tetris.application.GameSnapshot;

/**
 * 게임 한 장면을 그리는 포트. GameScreen은 이 인터페이스만 알고, 그리는 방식은 구현체가 정한다.
 * 지금은 텍스트 격자로 그리는 {@link TextRenderer}만 있다.
 */
public interface Renderer {

    TextFrame render(GameSnapshot snapshot);
}
