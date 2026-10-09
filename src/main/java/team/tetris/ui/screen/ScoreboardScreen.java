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
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 순위표. 메뉴에서 들어오면 보기만 하고 메뉴로 돌아가며, 게임이 끝난 뒤 들어오면 방금 등록한 기록을
 * 강조하고 "메뉴로 / 프로그램 종료"를 고를 수 있다.
 */
public final class ScoreboardScreen implements Screen {

    private static final int VISIBLE_ROWS = 10;
    private static final int LIST_TOP = 5;
    private static final int RANK_COL = 3;
    private static final int NAME_COL = 8;
    private static final int SCORE_END = 42;

    private final ScreenRouter router;
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
    public TextFrame render() {
        ColorPalette palette = router.palette();
        TextFrame frame = TextFrame.blank(palette.base());
        frame.putCentered(1, "스코어보드", palette.accentStyle());
        frame.putCentered(2, (mode == GameMode.NORMAL ? "일반" : "아이템") + " / " + difficulty.name(),
                palette.accentStyle());
        frame.put(RANK_COL, LIST_TOP - 2, "순위", palette.dimStyle());
        frame.put(NAME_COL, LIST_TOP - 2, "이름", palette.dimStyle());
        frame.putRight(SCORE_END, LIST_TOP - 2, "점수", palette.dimStyle());
        frame.put(RANK_COL, LIST_TOP - 1, "─".repeat(SCORE_END - RANK_COL), palette.dimStyle());

        if (entries.isEmpty()) {
            frame.putCentered(LIST_TOP + 3, "아직 기록이 없습니다", palette.dimStyle());
        }
        for (int i = 0; i < Math.min(VISIBLE_ROWS, entries.size()); i++) {
            ScoreEntry entry = entries.get(i);
            int row = LIST_TOP + i;
            boolean highlighted = highlight.isPresent() && highlight.get().equals(entry.recordId());
            TextStyle style = highlighted ? palette.highlightStyle() : palette.base();
            if (highlighted) {
                frame.fill(RANK_COL - 1, row, SCORE_END - RANK_COL + 2, 1, style);
            }
            frame.putRight(RANK_COL + 3, row, (i + 1) + ".", style);
            frame.put(NAME_COL, row, entry.name(), style);
            frame.putRight(SCORE_END, row, Long.toString(entry.score()), style);
        }

        if (isAfterGame()) {
            frame.putCentered(LIST_TOP + VISIBLE_ROWS + 1, "이번 점수 " + result.score(), palette.base().asBold());
        }
        error.ifPresent(failure -> {
            frame.putCentered(LIST_TOP + VISIBLE_ROWS + 3, "기록을 불러오지 못했습니다", palette.warningStyle());
            frame.putCentered(LIST_TOP + VISIBLE_ROWS + 4, "R 다시 시도", palette.warningStyle());
        });
        frame.putCentered(TextFrame.ROWS - 4, "←→ 난이도   ↑↓ 모드", palette.dimStyle());
        if (showKeyHint) {
            frame.putCentered(TextFrame.ROWS - 3, "사용할 수 있는 키: " + keyList(), palette.warningStyle());
        }
        frame.putCentered(TextFrame.ROWS - 2, isAfterGame() ? "Enter 메뉴로   Esc 프로그램 종료" : "Enter/Esc 메뉴로",
                palette.dimStyle());
        return frame;
    }

    private String keyList() {
        return "← → ↑ ↓ Enter Esc" + (error.isPresent() ? " R" : "");
    }
}
