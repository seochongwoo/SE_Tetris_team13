package team.tetris.ui.render;

import team.tetris.application.network.*;
import team.tetris.core.Cell;
import team.tetris.ui.render.palette.*;

/** 두 보드와 NEXT·대기 공격을 같은 격자에 그린다. 로컬/원격 모두 같은 화면 DTO를 사용한다. */
public final class P2pRenderer {
    private final ColorPalette palette;
    private final BoardPainter painter;
    public P2pRenderer(ColorPalette palette) {
        this.palette = palette;
        painter = new BoardPainter(palette, 0, 0);
    }

    public TextFrame render(P2pMatch match) {
        var frame = new TextFrame(88, 28, palette.base());
        frame.putCentered(0, "P2P · " + match.mode() + (match.mode() == MatchMessage.Mode.TIMED
                ? " · " + ((match.remainingNanos() + 999_999_999L) / 1_000_000_000L) + "초" : ""), palette.accentStyle());
        player(frame, 1, match.local(), "나 (" + match.role() + ")");
        match.remote().ifPresentOrElse(state -> player(frame, 45, state, "상대"),
                () -> frame.put(46, 3, "상대 화면 수신 대기", palette.base()));
        if (match.paused() && match.result().isEmpty())
            frame.putCentered(24, "일시정지 · " + match.pauseOwner() + "만 재개 가능", palette.accentStyle());
        match.result().ifPresent(result -> {
            frame.putCentered(24, switch (result.outcome()) {
                case DRAW -> "무승부";
                case SERVER_WINS -> "서버 승리";
                case CLIENT_WINS -> "클라이언트 승리";
            }, palette.accentStyle());
            frame.putCentered(25, "잠시 후 대기실로 돌아갑니다", palette.base());
        });
        return frame;
    }

    private void player(TextFrame frame, int left, PlayerState state, String label) {
        frame.put(left, 1, label, palette.accentStyle());
        frame.box(left, 2, 22, 22, palette.borderStyle(), palette.base());
        for (int y = 0; y < 20; y++) for (int x = 0; x < 10; x++)
            cell(frame, left + 1 + x * 2, 3 + y, PlayerStates.cell(state.board().get(y * 10 + x)));
        for (var tile : state.active()) if (tile.y() >= 0)
            cell(frame, left + 1 + tile.x() * 2, 3 + tile.y(), PlayerStates.cell(tile.tile()));
        int panel = left + 23;
        frame.put(panel, 2, "NEXT", palette.base());
        for (var tile : state.next()) cell(frame, panel + tile.x() * 2, 4 + tile.y(), PlayerStates.cell(tile.tile()));
        frame.put(panel, 7, "점수 " + state.score(), palette.base());
        frame.put(panel, 8, "레벨 " + state.level(), palette.base());
        frame.put(panel, 9, "줄 " + state.clearedLines(), palette.base());
        frame.put(panel, 10, "2배 " + state.doubleScoreClears(), palette.base());
        frame.put(panel, 12, "대기 " + state.pendingRows().size(), palette.base());
        var rows = PlayerStates.rows(state.pendingRows());
        for (int y = 0; y < rows.size(); y++) for (int x = 0; x < 10; x++)
            cell(frame, panel + x * 2, 13 + y, rows.get(y).get(x));
    }

    private void cell(TextFrame frame, int x, int y, Cell cell) {
        if (cell.isEmpty()) frame.putTile(x, y, BlockGlyphs.EMPTY, palette.dimStyle(), palette.emptyTile());
        else painter.drawCell(frame, x, y, cell.occupiedBy(), cell.item());
    }
}
