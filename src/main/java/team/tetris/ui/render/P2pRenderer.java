package team.tetris.ui.render;

import team.tetris.application.network.*;
import team.tetris.core.Cell;
import team.tetris.core.Position;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.render.palette.*;

/** 두 보드와 NEXT·대기 공격을 같은 격자에 그린다. 로컬/원격 모두 같은 화면 DTO를 사용한다. */
public final class P2pRenderer {
    private final ColorPalette palette;
    private final BattleInfoPanel panel;
    private final BoardPainter painter;
    public P2pRenderer(ColorPalette palette) {
        this.palette = palette;
        panel = new BattleInfoPanel(palette);
        painter = new BoardPainter(palette, 0, 0);
    }

    public TextFrame render(P2pMatch match) {
        // 긴 조작 안내가 1P 받을 줄 미리보기의 아래 테두리를 덮지 않도록 전용 행을 둔다.
        var frame = new TextFrame(BattleRenderer.COLUMNS, BattleRenderer.ROWS + 1, palette.base());
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
        player(frame, BattleRenderer.ONE_LEFT, match.local(), "나");
        match.remote().ifPresentOrElse(state -> player(frame, BattleRenderer.TWO_LEFT, state, "상대"),
                () -> frame.put(BattleRenderer.TWO_LEFT, 3, "상대 화면 수신 대기", palette.dimStyle()));
        return frame;
    }

    private void player(TextFrame frame, int left, PlayerState state, String label) {
        int top = BattleRenderer.BOARD_TOP;
        frame.box(left, top, 22, 22, palette.borderStyle(), palette.base());
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
        panel.draw(frame, left, label, state.level() - 1, state.score(), state.clearedLines(),
                state.doubleScoreClears(), PlayerState.WIDTH, PlayerStates.rows(state.pendingRows()));
    }

    private void cell(TextFrame frame, int x, int y, Cell cell) {
        if (cell.isEmpty()) frame.putTile(x, y, BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
        else painter.drawCell(frame, x, y, cell.occupiedBy(), cell.item());
    }
}
