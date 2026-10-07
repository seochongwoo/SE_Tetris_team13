package team.tetris.ui.render;

import java.awt.Color;
import java.util.Objects;

/** 한 칸에 찍히는 글자의 색과 굵기. */
public record TextStyle(Color foreground, Color background, boolean bold) {

    public TextStyle {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
    }

    public TextStyle withForeground(Color color) {
        return new TextStyle(color, background, bold);
    }

    public TextStyle withBackground(Color color) {
        return new TextStyle(foreground, color, bold);
    }

    public TextStyle asBold() {
        return new TextStyle(foreground, background, true);
    }
}
