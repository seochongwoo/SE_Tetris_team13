package team.tetris.ui;

import java.util.function.Supplier;
import team.tetris.application.ApplicationContext;
import team.tetris.application.EndGameCoordinator;
import team.tetris.application.ScoreboardService;
import team.tetris.application.SettingsService;
import team.tetris.application.SinglePlayerSession;
import team.tetris.application.StartedGame;
import team.tetris.application.model.Settings;
import team.tetris.application.port.ScoreRepository;
import team.tetris.application.port.SettingsRepository;
import team.tetris.core.PlayerEngine;
import team.tetris.core.TetrominoType;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.SevenBagGenerator;
import team.tetris.storage.memory.InMemoryScoreRepository;
import team.tetris.storage.memory.InMemorySettingsRepository;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.screen.GameScreen;

/**
 * UI 테스트용 ApplicationContext. 파일 대신 메모리 저장소를 쓰고, 블록 생성기를 바꿔 끼워
 * 게임 진행을 결정적으로 재현할 수 있다. 라우터를 만들면 종료 요청 횟수와 적용된 설정을 기록한다.
 */
public final class TestApplication implements ApplicationContext {

    /** 약 60fps의 한 프레임. */
    public static final long FRAME = 16_000_000L;

    private final SettingsService settings;
    private final ScoreboardService scores;
    private final EndGameCoordinator endings;
    private Supplier<PieceGenerator> generators = () -> new SevenBagGenerator(42L);
    private int exits;
    private Settings applied;

    public TestApplication() {
        this(new InMemorySettingsRepository(), new InMemoryScoreRepository());
    }

    public TestApplication(SettingsRepository settingsRepository, ScoreRepository scoreRepository) {
        this.settings = new SettingsService(settingsRepository);
        this.scores = new ScoreboardService(scoreRepository);
        this.endings = new EndGameCoordinator(scores);
    }

    /** 모든 게임이 type 블록만 내놓도록 한다. */
    public TestApplication withPieces(TetrominoType type) {
        generators = () -> constant(type);
        return this;
    }

    @Override
    public SettingsService settings() {
        return settings;
    }

    @Override
    public ScoreboardService scores() {
        return scores;
    }

    @Override
    public EndGameCoordinator endings() {
        return endings;
    }

    @Override
    public StartedGame newGame() {
        return new StartedGame(new SinglePlayerSession(new PlayerEngine(10, 20, generators.get())),
                settings.loadOrDefault());
    }

    public ScreenRouter router() {
        return new ScreenRouter(this, settings.loadOrDefault(), () -> exits++, next -> applied = next);
    }

    public int exits() {
        return exits;
    }

    public Settings applied() {
        return applied;
    }

    public static PieceGenerator constant(TetrominoType type) {
        return new PieceGenerator() {
            @Override
            public TetrominoType next() {
                return type;
            }

            @Override
            public TetrominoType peek() {
                return type;
            }
        };
    }

    /** 게임 화면에서 하드드롭만 반복해 게임을 끝내고, GAME OVER 대기를 Enter로 넘긴다. */
    public static void playUntilGameOver(ScreenRouter router) {
        GameScreen game = (GameScreen) router.current();
        for (int i = 0; i < 200 && game.session().result().isEmpty(); i++) {
            router.keyPressed("SPACE");
            router.keyReleased("SPACE");
            router.update(FRAME);
        }
        router.keyPressed(KeyNames.ENTER);
        router.update(FRAME);
    }
}
