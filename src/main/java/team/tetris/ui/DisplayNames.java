package team.tetris.ui;

import team.tetris.application.model.Difficulty;
import team.tetris.application.model.Settings.ScreenSize;

/** 화면에 보이는 값 이름. 설정, 게임, 스코어보드 화면이 같은 한글 이름을 쓰도록 한곳에 둔다. */
public final class DisplayNames {

    private DisplayNames() {
    }

    public static String of(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "쉬움";
            case NORMAL -> "보통";
            case HARD -> "어려움";
        };
    }

    public static String of(ScreenSize size) {
        return switch (size) {
            case SMALL -> "작게";
            case MEDIUM -> "보통";
            case LARGE -> "크게";
        };
    }

    public static String onOff(boolean value) {
        return value ? "켜짐" : "꺼짐";
    }
}
