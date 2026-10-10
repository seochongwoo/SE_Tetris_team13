package team.tetris.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.function.Function;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.GameMode;
import team.tetris.application.*;
import team.tetris.core.PlayerEngine;
import team.tetris.core.item.ItemCatalog;
import team.tetris.core.item.ItemKind;
import team.tetris.core.rule.ItemPieceSource;
import team.tetris.core.rule.PieceGenerator;
import team.tetris.core.rule.PieceSource;
import team.tetris.core.rule.PlainPieceSource;
import team.tetris.core.rule.WeightedRandomGenerator;
import team.tetris.storage.BinaryScoreRepository;
import team.tetris.storage.PropertiesSettingsRepository;

/** 저장소·서비스·정책·엔진 조립. 게임 규칙과 정렬은 각 구성 요소에 위임. */
public final class AppComposition implements ApplicationContext {
    private final SettingsService settings;
    private final ScoreboardService scores;
    private final EndGameCoordinator endings;
    private final Function<Difficulty, ? extends PieceGenerator> generators;
    private final ScoreRule scoring;
    private final Function<Difficulty, ? extends SpeedRule> speeds;
    private final List<ItemKind> itemKinds;

    /** 실제 게임 구성: 저장된 난이도별 가중치 생성기와 속도 규칙, {@link ItemCatalog}의 아이템. */
    public AppComposition(Path dataDirectory) {
        this(dataDirectory, AppComposition::generatorFor, new ScorePolicy(), SpeedPolicy::new, ItemCatalog.all());
    }

    /** 새 게임마다 독립된 블록 생성기를 반환하는 팩터리 주입 필요. */
    public AppComposition(Path dataDirectory, Supplier<? extends PieceGenerator> generators,
                          ScoreRule scoring, SpeedRule speed) {
        this(dataDirectory, generators, scoring, difficulty -> speed);
        Objects.requireNonNull(speed, "speed");
    }

    /** 저장된 난이도로 매 게임의 속도 규칙을 생성한다. 아이템 모드에는 {@link ItemCatalog}의 아이템을 쓴다. */
    public AppComposition(Path dataDirectory, Supplier<? extends PieceGenerator> generators,
                          ScoreRule scoring, Function<Difficulty, ? extends SpeedRule> speeds) {
        this(dataDirectory, generators, scoring, speeds, ItemCatalog.all());
    }

    /** 아이템 모드에 등장할 아이템 목록까지 주입. 블록 생성기는 난이도와 무관하게 generators가 만든다. */
    public AppComposition(Path dataDirectory, Supplier<? extends PieceGenerator> generators,
                          ScoreRule scoring, Function<Difficulty, ? extends SpeedRule> speeds,
                          List<ItemKind> itemKinds) {
        this(dataDirectory, ignoringDifficulty(generators), scoring, speeds, itemKinds);
    }

    /** 저장된 난이도로 매 게임의 블록 생성기와 속도 규칙을 만든다. */
    public AppComposition(Path dataDirectory, Function<Difficulty, ? extends PieceGenerator> generators,
                          ScoreRule scoring, Function<Difficulty, ? extends SpeedRule> speeds,
                          List<ItemKind> itemKinds) {
        this.itemKinds = List.copyOf(itemKinds);
        Path directory = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.generators = Objects.requireNonNull(generators, "generators");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.speeds = Objects.requireNonNull(speeds, "speeds");
        settings = new SettingsService(new PropertiesSettingsRepository(directory.resolve("settings.properties")));
        scores = new ScoreboardService(new BinaryScoreRepository(directory.resolve("scores.bin")));
        endings = new EndGameCoordinator(scores);
    }

    /** 실제 게임이 쓰는 블록 생성기: 난이도별 가중치로 매번 독립적으로 뽑는다. */
    static PieceGenerator generatorFor(Difficulty difficulty) {
        return generatorFor(difficulty, ThreadLocalRandom.current().nextLong());
    }

    static PieceGenerator generatorFor(Difficulty difficulty, long seed) {
        return new WeightedRandomGenerator(PieceWeightPolicy.weightsFor(difficulty), new Random(seed));
    }

    private static Function<Difficulty, PieceGenerator> ignoringDifficulty(Supplier<? extends PieceGenerator> generators) {
        Objects.requireNonNull(generators, "generators");
        return difficulty -> generators.get();
    }

    @Override
    public SettingsService settings() { return settings; }
    @Override
    public ScoreboardService scores() { return scores; }
    @Override
    public EndGameCoordinator endings() { return endings; }

    @Override
    public StartedGame newGame(GameMode mode) {
        Objects.requireNonNull(mode, "mode");
        var loaded = settings.loadOrDefault();
        var difficulty = loaded.value().difficulty();
        var speed = Objects.requireNonNull(speeds.apply(difficulty), "speed");
        PieceSource pieces = new PlainPieceSource(Objects.requireNonNull(generators.apply(difficulty), "generator"));
        if (mode == GameMode.ITEM) {
            pieces = new ItemPieceSource(pieces, itemKinds, new Random(ThreadLocalRandom.current().nextLong()));
        }
        var engine = new PlayerEngine(10, 20, pieces);
        var session = new SinglePlayerSession(engine, scoring, speed, mode, difficulty);
        return new StartedGame(session, loaded);
    }
}
