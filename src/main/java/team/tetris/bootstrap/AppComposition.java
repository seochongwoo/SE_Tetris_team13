package team.tetris.bootstrap;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.function.Function;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.*;
import team.tetris.core.PlayerEngine;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.SevenBagGenerator;
import team.tetris.storage.BinaryScoreRepository;
import team.tetris.storage.PropertiesSettingsRepository;

/** 저장소·서비스·정책·엔진 조립. 게임 규칙과 정렬은 각 구성 요소에 위임. */
public final class AppComposition implements ApplicationContext {
    private final SettingsService settings;
    private final ScoreboardService scores;
    private final EndGameCoordinator endings;
    private final Supplier<? extends PieceGenerator> generators;
    private final ScoreRule scoring;
    private final Function<Difficulty, ? extends SpeedRule> speeds;

    public AppComposition(Path dataDirectory) {
        this(dataDirectory, () -> new SevenBagGenerator(ThreadLocalRandom.current().nextLong()),
                new ScorePolicy(), SpeedPolicy::new);
    }

    /** 새 게임마다 독립된 블록 생성기를 반환하는 팩터리 주입 필요. */
    public AppComposition(Path dataDirectory, Supplier<? extends PieceGenerator> generators,
                          ScoreRule scoring, SpeedRule speed) {
        this(dataDirectory, generators, scoring, difficulty -> speed);
        Objects.requireNonNull(speed, "speed");
    }

    /** 저장된 난이도로 매 게임의 속도 규칙을 생성한다. */
    public AppComposition(Path dataDirectory, Supplier<? extends PieceGenerator> generators,
                          ScoreRule scoring, Function<Difficulty, ? extends SpeedRule> speeds) {
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.generators = Objects.requireNonNull(generators, "generators");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.speeds = Objects.requireNonNull(speeds, "speeds");
        settings = new SettingsService(new PropertiesSettingsRepository(directory.resolve("settings.properties")));
        scores = new ScoreboardService(new BinaryScoreRepository(directory.resolve("scores.bin")));
        endings = new EndGameCoordinator(scores);
    }

    @Override
    public SettingsService settings() { return settings; }
    @Override
    public ScoreboardService scores() { return scores; }
    @Override
    public EndGameCoordinator endings() { return endings; }

    @Override
    public StartedGame newGame() {
        var loaded = settings.loadOrDefault();
        var speed = Objects.requireNonNull(speeds.apply(loaded.value().difficulty()), "speed");
        var engine = new PlayerEngine(10, 20, Objects.requireNonNull(generators.get(), "generator"));
        var session = new SinglePlayerSession(engine, scoring, speed, GameMode.NORMAL, loaded.value().difficulty());
        return new StartedGame(session, loaded);
    }
}
