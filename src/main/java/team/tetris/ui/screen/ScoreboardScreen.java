package team.tetris.ui.screen;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import team.tetris.application.EndGameView;
import team.tetris.application.GameResult;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.GameMode;
import team.tetris.application.model.Difficulty;
import team.tetris.application.model.ScoreEntry;
import team.tetris.application.port.StorageException;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.MenuTheme;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 순위표. 메뉴에서 들어오면 보기만 하고 메뉴로 돌아가며, 게임이 끝난 뒤 들어오면 방금 등록한 기록을
 * 강조하고 "메뉴로 / 프로그램 종료"를 고를 수 있다.
 */
public final class ScoreboardScreen implements Screen {

    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_TOP = 6;
    /** 제목 두 줄을 담는 위 상자의 줄 수. 열 이름부터는 가운데 상자에 둔다. */
    private static final int HEADER_ROWS = 4;
    /** 아래 안내 상자가 시작하는 행. 안쪽 두 줄에 키 안내를 둔다. */
    private static final int FOOTER_TOP = TextFrame.ROWS - 4;
    private static final int RANK_COL = 3;
    private static final int NAME_COL = 8;
    private static final int SCORE_END = 42;

    private final ScreenRouter router;
    /** 배경 구름이 흘러가도록 쌓는 시간. */
    private long clockNanos;
    private List<ScoreEntry> entries;
    private Optional<UUID> highlight;
    private Optional<StorageException> error;
    private GameMode mode;
    private Difficulty difficulty;
    private final GameResult result;
    private boolean showKeyHint;

    private ScoreboardScreen(ScreenRouter router, List<ScoreEntry> entries, Optional<UUID> highlight,
                             Optional<StorageException> error, GameResult result) {
        this.router = router;
        this.entries = List.copyOf(entries);
        this.highlight = highlight;
        this.error = error;
        this.result = result;
        mode = result == null ? GameMode.NORMAL : result.mode();
        difficulty = result == null ? router.settings().difficulty() : result.difficulty();
    }

    public static ScoreboardScreen fromMenu(ScreenRouter router, LoadResult<List<ScoreEntry>> loaded) {
        return new ScoreboardScreen(router, loaded.value(), Optional.empty(), loaded.error(), null);
    }

    public static ScoreboardScreen afterGame(ScreenRouter router, GameResult result, EndGameView view) {
        return new ScoreboardScreen(router, view.scores(), view.highlightedRecordId(), view.storageError(), result);
    }

    public List<ScoreEntry> entries() {
        return entries;
    }

    public Optional<UUID> highlightedRecordId() {
        return highlight;
    }

    public boolean isAfterGame() {
        return result != null;
    }

    @Override
    public void onKeyPressed(String key) {
        switch (key) {
            case KeyNames.ENTER -> router.showMenu();
            case KeyNames.ESCAPE -> {
                if (isAfterGame()) {
                    router.exit();
                } else {
                    router.showMenu();
                }
            }
            case KeyNames.LEFT -> changeDifficulty(-1);
            case KeyNames.RIGHT -> changeDifficulty(1);
            case KeyNames.UP, KeyNames.DOWN -> {
                mode = mode == GameMode.NORMAL ? GameMode.ITEM : GameMode.NORMAL;
                reload();
            }
            case "R" -> {
                if (error.isPresent()) {
                    reload();
                } else {
                    showKeyHint = true;
                }
            }
            default -> showKeyHint = true;
        }
    }

    private void changeDifficulty(int direction) {
        Difficulty[] choices = Difficulty.values();
        difficulty = choices[Math.floorMod(difficulty.ordinal() + direction, choices.length)];
        reload();
    }

    private void reload() {
        var loaded = router.loadScores(mode, difficulty);
        entries = loaded.value();
        error = loaded.error();
        // 최초 조회가 실패했어도 복구한 목록에서 이번 게임의 기록을 다시 찾는다.
        highlight = result == null ? Optional.empty() : entries.stream()
                .filter(entry -> entry.gameId().equals(result.gameId()))
                .map(ScoreEntry::recordId).findFirst();
        showKeyHint = false;
    }

    @Override
    public void update(long elapsedNanos) {
        clockNanos += elapsedNanos;
    }

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        TextFrame frame = TextFrame.blank(palette.base());
        // 제목 두 줄은 위 상자, 열 이름·순위·이번 점수는 가운데 상자, 키 안내는 아래 상자. 글자가 상자 테두리
        // 줄에 걸치지 않도록 각 상자의 첫 줄과 마지막 줄은 비운다.
        MenuTheme.panels(frame, clockNanos, HEADER_ROWS, FOOTER_TOP);
        frame.putCentered(1, "스코어보드", palette.accentStyle());
        frame.putCentered(2, (mode == GameMode.NORMAL ? "일반" : "아이템") + " / " + DisplayNames.of(difficulty),
                palette.accentStyle());
        frame.put(RANK_COL, LIST_TOP - 1, "순위", palette.dimStyle());
        frame.put(NAME_COL, LIST_TOP - 1, "이름", palette.dimStyle());
        frame.putRight(SCORE_END, LIST_TOP - 1, "점수", palette.dimStyle());

        if (entries.isEmpty()) {
            frame.putCentered(LIST_TOP + 3, "아직 기록이 없습니다", palette.dimStyle());
        }
        for (int i = 0; i < Math.min(VISIBLE_ROWS, entries.size()); i++) {
            ScoreEntry entry = entries.get(i);
            int row = LIST_TOP + i;
            boolean highlighted = highlight.isPresent() && highlight.get().equals(entry.recordId());
            TextStyle style = highlighted ? palette.highlightStyle() : palette.base();
            if (highlighted) {
                frame.fill(RANK_COL - 1, row, SCORE_END - RANK_COL + 1, 1, style); // 상자 오른쪽 테두리 칸은 덮지 않는다
            }
            frame.putRight(RANK_COL + 3, row, (i + 1) + ".", style);
            frame.put(NAME_COL, row, entry.name(), style);
            frame.putRight(SCORE_END, row, Long.toString(entry.score()), style);
        }

        if (isAfterGame()) {
            frame.putCentered(LIST_TOP + VISIBLE_ROWS + 1, "이번 점수 " + result.score(), palette.base().asBold());
        }
        error.ifPresent(failure -> {
            frame.putCentered(LIST_TOP + VISIBLE_ROWS + 2, "기록을 불러오지 못했습니다 · R 다시 시도", palette.warningStyle());
        });
        // 다른 키를 누르면 같은 줄에 사용할 수 있는 키를 경고색으로 보여 준다 (시작 메뉴와 같은 방식).
        if (showKeyHint) {
            frame.putCentered(FOOTER_TOP + 1, "사용할 수 있는 키: " + keyList(), palette.warningStyle());
        } else {
            frame.putCentered(FOOTER_TOP + 1, "←→ 난이도   ↑↓ 모드", palette.dimStyle());
        }
        frame.putCentered(TextFrame.ROWS - 2, isAfterGame() ? "Enter 메뉴로   Esc 프로그램 종료" : "Enter/Esc 메뉴로",
                palette.dimStyle());
        return frame;
    }

    private String keyList() {
        return "← → ↑ ↓ Enter Esc" + (error.isPresent() ? " R" : "");
    }
}
