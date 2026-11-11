package team.tetris.ui.render;

import java.util.List;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.model.GameMode;
import team.tetris.core.AttackBlock;
import team.tetris.core.Cell;
import team.tetris.core.rule.AttackQueue;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.input.BattleKeys;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 로컬 대전 화면을 그린다 (3차 카드 19). 1P는 왼쪽, 2P는 오른쪽에 각자의 보드, NEXT, 점수, 받을 줄 영역을
 * 둔다. 1인 화면보다 넓은 격자({@value #COLUMNS}열)를 쓴다.
 *
 * <pre>
 * 0행        모드·난이도                                       공통 키 안내
 * 1~22행     [1P 보드][1P 정보·받을 줄]  [2P 보드][2P 정보·받을 줄]
 * 23행       1P 조작 키                  2P 조작 키
 * </pre>
 *
 * <p>받을 줄 영역은 상대가 보낸 공격 줄이 보드에 들어가기 전에 먼저 보여 주는 곳이다 (R3-07). 줄은
 * 보드에 들어갈 모양 그대로, 맨 아래 줄이 보드 맨 아래에 붙을 줄이 되도록 아래에 맞춰 그린다.
 */
public final class BattleRenderer {

    public static final int COLUMNS = 78;
    public static final int ROWS = TextFrame.ROWS;
    static final int BOARD_TOP = 1;
    /** 플레이어 영역의 왼쪽 열: 보드(22) + 1칸 + 정보(14) = 37칸씩, 사이에 2칸. */
    static final int ONE_LEFT = 1;
    static final int TWO_LEFT = 40;
    private static final int INFO_OFFSET = 23;
    private static final int INFO_WIDTH = 14;
    private static final int NEXT_TITLE_ROW = 2;
    private static final int SCORE_ROW = NEXT_TITLE_ROW + BoardPainter.NEXT_BOX_HEIGHT + 1;
    private static final int PENDING_TITLE_ROW = SCORE_ROW + 2;
    /** 받을 줄 상자: 안쪽 폭은 보드 칸 수(한 칸 = 한 글자), 안쪽 높이는 대기열 최대 줄 수. */
    private static final int PENDING_BOX_TOP = PENDING_TITLE_ROW + 1;
    private static final int HELP_ROW = ROWS - 1;
    static final String PENDING_CELL = "█";
    static final String PENDING_EMPTY = "·";

    private final ColorPalette palette;
    private final BattleKeys keys;

    public BattleRenderer(ColorPalette palette, BattleKeys keys) {
        this.palette = palette;
        this.keys = keys;
    }

    /** player 영역의 왼쪽 열 (보드 테두리의 왼쪽 열). */
    public static int leftOf(BattlePlayer player) {
        return player == BattlePlayer.ONE ? ONE_LEFT : TWO_LEFT;
    }

    /** player 보드를 그리는 도구. 화면은 이것으로 보드 칸의 위치를 구한다. */
    public BoardPainter boardOf(BattlePlayer player) {
        return new BoardPainter(palette, leftOf(player), BOARD_TOP);
    }

    public TextFrame render(BattleSnapshot snapshot) {
        TextFrame frame = new TextFrame(COLUMNS, ROWS, palette.base());
        drawHeader(frame, snapshot);
        for (BattlePlayer player : BattlePlayer.values()) {
            drawPlayer(frame, player, snapshot.of(player), snapshot.pendingFor(player));
        }
        return frame;
    }

    private void drawHeader(TextFrame frame, BattleSnapshot snapshot) {
        String mode = snapshot.mode() == GameMode.ITEM ? "아이템" : "일반";
        frame.put(ONE_LEFT, 0, "대전 · " + mode + " / " + DisplayNames.of(snapshot.difficulty()), palette.accentStyle());
        String common = KeyNames.display(keys.pauseKey()) + " 일시정지  " + KeyNames.display(keys.menuKey()) + " 메뉴";
        frame.putRight(COLUMNS - 1, 0, common, palette.dimStyle());
    }

    private void drawPlayer(TextFrame frame, BattlePlayer player, GameSnapshot game, List<List<Cell>> pending) {
        int left = leftOf(player);
        BoardPainter board = boardOf(player);
        board.drawPlayfield(frame, game, false);
        int info = left + INFO_OFFSET;
        frame.put(info, BOARD_TOP, label(player), palette.accentStyle().asBold());
        frame.putRight(info + INFO_WIDTH, BOARD_TOP, "LV " + game.level(), palette.dimStyle());
        board.drawNext(frame, game.engine().nextPiece(), info, NEXT_TITLE_ROW);
        frame.put(info, SCORE_ROW, "SCORE", palette.dimStyle());
        frame.putRight(info + INFO_WIDTH, SCORE_ROW, Long.toString(game.score()), palette.base().asBold());
        frame.put(info, SCORE_ROW + 1, "LINES", palette.dimStyle());
        frame.putRight(info + INFO_WIDTH, SCORE_ROW + 1, Integer.toString(game.clearedLines()), palette.base().asBold());
        if (game.doubleScoreClears() > 0) {
            // 점수 2배 아이템($)이 남은 줄 삭제 횟수
            frame.put(info + 6, SCORE_ROW, "x2(" + game.doubleScoreClears() + ")", palette.accentStyle());
        }
        drawPending(frame, info, game.engine().board()[0].length, pending);
        drawHelp(frame, player, left);
    }

    /** 받을 줄: 위에서 아래 순서의 대기열을 상자 아래쪽에 맞춰 그린다. 비어 있는 위쪽은 빈 줄이다. */
    private void drawPending(TextFrame frame, int col, int width, List<List<Cell>> pending) {
        TextStyle attack = palette.blockStyle(AttackBlock.INSTANCE);
        frame.put(col, PENDING_TITLE_ROW, "받을 줄", palette.dimStyle());
        frame.putRight(col + INFO_WIDTH, PENDING_TITLE_ROW, pending.size() + "/" + AttackQueue.MAX_LINES,
                pending.isEmpty() ? palette.dimStyle() : palette.warningStyle());
        frame.box(col, PENDING_BOX_TOP, width + 2, AttackQueue.MAX_LINES + 2, palette.borderStyle(), palette.base());
        int firstRow = PENDING_BOX_TOP + 1 + AttackQueue.MAX_LINES - pending.size();
        for (int i = 0; i < pending.size(); i++) {
            List<Cell> line = pending.get(i);
            for (int x = 0; x < line.size() && x < width; x++) {
                boolean empty = line.get(x).isEmpty();
                frame.put(col + 1 + x, firstRow + i, empty ? PENDING_EMPTY : PENDING_CELL,
                        empty ? palette.dimStyle() : attack);
            }
        }
    }

    private void drawHelp(TextFrame frame, BattlePlayer player, int left) {
        StringBuilder text = new StringBuilder(label(player)).append(' ');
        for (GameCommand command : List.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT, GameCommand.ROTATE_CW,
                GameCommand.SOFT_DROP)) {
            text.append(KeyNames.display(keys.keyFor(player, command))).append(' ');
        }
        text.append("· ").append(KeyNames.display(keys.keyFor(player, GameCommand.HARD_DROP)));
        frame.put(left, HELP_ROW, text.toString(), palette.dimStyle());
    }

    static String label(BattlePlayer player) {
        return player == BattlePlayer.ONE ? "1P" : "2P";
    }
}
