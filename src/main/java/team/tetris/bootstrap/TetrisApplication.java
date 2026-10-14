package team.tetris.bootstrap;

import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import team.tetris.application.port.GameUi;

/** UI 제공자를 통해 실행 조립부 연결. 사용자 데이터 경로의 명시적 지정 지원. */
public final class TetrisApplication {
    private TetrisApplication() {}

    public static void main(String[] args) {
        LaunchOptions options = parseOptions(args, Path.of(System.getProperty("user.home")));
        GameUi ui = ServiceLoader.load(GameUi.class).findFirst().orElseThrow(() ->
                new IllegalStateException("No GameUi provider installed. Register the UI implementation in "
                        + "META-INF/services/team.tetris.application.port.GameUi"));
        ui.open(new AppComposition(options.directory(), options.debug()));
    }

    /** UI 직접 주입으로 실행 및 통합 테스트 지원. */
    public static void launch(Path dataDirectory, GameUi ui) {
        Objects.requireNonNull(ui, "ui").open(new AppComposition(dataDirectory));
    }

    static Path dataDirectory(String[] args, Path homeDirectory) {
        return parseOptions(args, homeDirectory).directory();
    }

    private record LaunchOptions(Path directory, boolean debug) { }

    private static LaunchOptions parseOptions(String[] args, Path homeDirectory) {
        Objects.requireNonNull(args, "args");
        Path directory = homeDirectory.resolve(".se-tetris-team13");
        boolean debug = false;
        boolean hasDirectory = false;
        for (int i = 0; i < args.length; i++) {
            if ("--debug".equals(args[i]) && !debug) {
                debug = true;
            } else if ("--data-dir".equals(args[i]) && !hasDirectory && i + 1 < args.length
                    && !args[i + 1].isBlank() && !args[i + 1].startsWith("--")) {
                directory = Path.of(args[++i]);
                hasDirectory = true;
            } else {
                throw new IllegalArgumentException("Usage: TetrisApplication [--debug] [--data-dir PATH]");
            }
        }
        return new LaunchOptions(directory, debug);
    }
}
