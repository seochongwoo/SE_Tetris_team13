package team.tetris.bootstrap;

import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import team.tetris.application.port.GameUi;

/** UI 제공자를 통해 실행 조립부 연결. 사용자 데이터 경로의 명시적 지정 지원. */
public final class TetrisApplication {
    private TetrisApplication() {}

    public static void main(String[] args) {
        Path directory = dataDirectory(args, Path.of(System.getProperty("user.home")));
        GameUi ui = ServiceLoader.load(GameUi.class).findFirst().orElseThrow(() ->
                new IllegalStateException("No GameUi provider installed. Register the UI implementation in "
                        + "META-INF/services/team.tetris.application.port.GameUi"));
        launch(directory, ui);
    }

    /** UI 직접 주입으로 실행 및 통합 테스트 지원. */
    public static void launch(Path dataDirectory, GameUi ui) {
        Objects.requireNonNull(ui, "ui").open(new AppComposition(dataDirectory));
    }

    static Path dataDirectory(String[] args, Path homeDirectory) {
        Objects.requireNonNull(args, "args");
        if (args.length == 0) return homeDirectory.resolve(".se-tetris-team13");
        if (args.length == 2 && "--data-dir".equals(args[0]) && !args[1].isBlank()) {
            return Path.of(args[1]);
        }
        throw new IllegalArgumentException("Usage: TetrisApplication [--data-dir PATH]");
    }
}
