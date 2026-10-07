package team.tetris.ui.screen;

import team.tetris.ui.render.TextFrame;

/**
 * 화면 하나. 키 입력과 프레임 갱신을 받아 자기 상태를 바꾸고, {@link #render()}로 현재 모습을
 * 텍스트 격자로 내놓는다. Swing을 전혀 모르므로 테스트에서 키 이름만 넣어 동작을 확인할 수 있다.
 */
public interface Screen {

    /** 이 화면으로 전환된 직후 한 번 호출된다. */
    default void onEnter() {
    }

    /** 키가 눌렸다. key는 {@code KeyNames}의 식별자 (예: LEFT, ENTER, P). */
    void onKeyPressed(String key);

    default void onKeyReleased(String key) {
    }

    /** 글자가 입력됐다 (이름 입력처럼 문자 자체가 필요한 화면용). */
    default void onCharTyped(char character) {
    }

    /**
     * 한글 같은 조합 문자를 입력받는 화면인가. true인 동안만 OS 입력기(IME)를 켠다.
     *
     * <p>입력기가 켜진 채 한글 모드이면 글자 키(P 등)의 눌림 이벤트가 입력기에 먹혀 게임 조작이
     * 무시되므로, 문자를 받지 않는 화면은 입력기를 끈다.
     */
    default boolean acceptsTextInput() {
        return false;
    }

    /** 창이 포커스를 잃었다. */
    default void onFocusLost() {
    }

    /** 한 프레임만큼 시간이 흘렀다. */
    default void update(long elapsedNanos) {
    }

    TextFrame render();

    /** 창의 X 버튼을 눌렀을 때 확인 없이 바로 꺼도 되는 화면인가. */
    default boolean allowsImmediateExit() {
        return true;
    }

    /** {@link #allowsImmediateExit()}가 false인 화면에서 X 버튼을 눌렀다 (확인 표시용). */
    default void onExitRequested() {
    }
}
