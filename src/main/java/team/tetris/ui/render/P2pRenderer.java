package team.tetris.ui.render;

import team.tetris.application.network.*;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.render.palette.*;

/**
 * 두 보드와 NEXT·대기 공격을 같은 격자에 그린다. 로컬/원격 모두 같은 화면 DTO를 사용한다. 배치·크기·플레이어
 * 색은 로컬 대전({@link BattleRenderer})과 같다: 나는 왼쪽(1P 색), 상대는 오른쪽(2P 색). 네트워크 지연은
 * 상대 보드 바로 아래 줄에 두고, 맨 아래 줄은 내 조작 안내가 다 쓴다.
 */
public final class P2pRenderer {
    private final ColorPalette palette;
    private final BattleInfoPanel panel;
    private final BoardPainter painter;
    /** 상대 보드 테두리 바로 아래 줄. 왼쪽 받을 줄 상자가 끝난 뒤부터 상대 보드 오른쪽 끝까지 비어 있다. */
    static final int LATENCY_ROW = BattleRenderer.BOARD_TOP + BattleRenderer.BOARD_OUTER_HEIGHT;
    /** 지연 글자가 끝나는 열의 바로 다음 열 ({@link TextFrame#putRight}의 끝 열). 상대 보드 오른쪽 테두리에 맞춘다. */
    static final int LATENCY_RIGHT = BattleRenderer.TWO_LEFT + BoardPainter.outerWidth(PlayerState.WIDTH);

    public P2pRenderer(ColorPalette palette) {
        this.palette = palette;
        panel = new BattleInfoPanel(palette);
        painter = new BoardPainter(palette, 0, 0);
    }

    public TextFrame render(P2pMatch match) {
        var frame = new TextFrame(BattleRenderer.COLUMNS, BattleRenderer.ROWS, palette.base());
        frame.put(BattleRenderer.ONE_LEFT, 0, "P2P 대전 · " + switch (match.mode()) {
            case NORMAL -> "일반";
            case ITEM -> "아이템";
            case TIMED -> "시간제한";
        } + " / " + DisplayNames.of(match.difficulty()), palette.accentStyle());
        if (match.mode() == MatchMessage.Mode.TIMED) {
            long seconds = (match.remainingNanos() + 999_999_999L) / 1_000_000_000L;
            frame.putCentered(0, "남은 시간 " + BattleRenderer.clock(seconds),
                    seconds <= BattleRenderer.WARNING_SECONDS ? palette.warningStyle() : palette.base().asBold());
        }
        drawLatency(frame, palette, match.roundTripNanos(), match.lagging(), LATENCY_ROW, LATENCY_RIGHT);
        player(frame, BattleRenderer.ONE_LEFT, match.local(), "나", 0);
        match.remote().ifPresentOrElse(state -> player(frame, BattleRenderer.TWO_LEFT, state, "상대", 1),
                () -> frame.put(BattleRenderer.TWO_LEFT, 3, "상대 화면 수신 대기", palette.dimStyle()));
        return frame;
    }

    public static void drawLatency(TextFrame frame, ColorPalette palette, java.util.OptionalLong nanos, boolean lagging) {
        drawLatency(frame, palette, nanos, lagging, frame.rows() - 1, frame.columns() - 1);
    }

    public static boolean hasLatency(java.util.OptionalLong nanos, boolean lagging) {
        return nanos.isPresent() || lagging;
    }

    /** 지연(ms)을 row 줄에 right 열 바로 앞에서 끝나게 그린다 (right는 포함하지 않음). 랙이면 경고색. */
    public static void drawLatency(TextFrame frame, ColorPalette palette, java.util.OptionalLong nanos, boolean lagging,
                                   int row, int right) {
        if (!hasLatency(nanos, lagging)) return;
        String label = lagging ? "네트워크 지연 (랙)" : "지연";
        if (nanos.isPresent()) label += " " + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos.getAsLong()) + " ms";
        frame.putRight(right, row, label, lagging ? palette.warningStyle() : palette.dimStyle());
    }

    private void player(TextFrame frame, int left, PlayerState state, String label, int playerIndex) {
        int top = BattleRenderer.BOARD_TOP;
        frame.box(left, top, BoardPainter.outerWidth(PlayerState.WIDTH), BattleRenderer.BOARD_OUTER_HEIGHT,
                palette.playerStyle(playerIndex), palette.base());
        for (int y = 0; y < PlayerState.HEIGHT; y++) {
            for (int x = 0; x < PlayerState.WIDTH; x++) {
                cell(frame, left + 1 + x * 2, top + 1 + y,
                        PlayerStates.cell(state.board().get(y * PlayerState.WIDTH + x)));
            }
        }
        for (var tile : state.active()) {
            if (tile.y() >= 0) cell(frame, left + 1 + tile.x() * 2, top + 1 + tile.y(), PlayerStates.cell(tile.tile()));
        }
        Position[] preview = state.next().stream().map(t -> new Position(t.x(), t.y())).toArray(Position[]::new);
        painter.drawNext(frame, preview, left + BattleInfoPanel.INFO_OFFSET, BattleInfoPanel.NEXT_TITLE_ROW,
                (i, x, y) -> cell(frame, x, y, PlayerStates.cell(state.next().get(i).tile())));
        panel.draw(frame, left, label, palette.playerStyle(playerIndex), state.level() - 1, state.score(), state.clearedLines(),
                state.doubleScoreClears(), PlayerState.WIDTH, PlayerStates.rows(state.pendingRows()));
    }

    private void cell(TextFrame frame, int x, int y, Cell cell) {
        if (cell.isEmpty()) frame.putTile(x, y, BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
        else painter.drawCell(frame, x, y, cell.occupiedBy(), cell.item());
    }
}
