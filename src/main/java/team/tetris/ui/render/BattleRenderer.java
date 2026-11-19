package team.tetris.ui.render;

import java.util.List;
import team.tetris.application.GameCommand;
import team.tetris.application.GameSnapshot;
import team.tetris.application.battle.BattlePlayer;
import team.tetris.application.battle.BattleSnapshot;
import team.tetris.application.battle.BattleTimer;
import team.tetris.application.model.GameMode;
import team.tetris.core.Cell;
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
    private static final int HELP_ROW = ROWS - 1;
    private final BattleInfoPanel panel;
    private final ColorPalette palette;
    private final BattleKeys keys;

    public BattleRenderer(ColorPalette palette, BattleKeys keys) {
        this.palette = palette;
        this.keys = keys;
        panel = new BattleInfoPanel(palette);
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
        BattleTimer timer = snapshot.timer();
        String mode = timer != null ? "시간제한" : snapshot.mode() == GameMode.ITEM ? "아이템" : "일반";
        frame.put(ONE_LEFT, 0, "대전 · " + mode + " / " + DisplayNames.of(snapshot.difficulty()), palette.accentStyle());
        if (timer != null) {
            // 남은 시간은 가운데. 마지막 10초는 경고색으로 바꾼다.
            frame.putCentered(0, "남은 시간 " + clock(timer.remainingSeconds()),
                    timer.remainingSeconds() <= WARNING_SECONDS ? palette.warningStyle() : palette.base().asBold());
        }
        String common = KeyNames.display(keys.pauseKey()) + " 일시정지  " + KeyNames.display(keys.menuKey()) + " 메뉴";
        frame.putRight(COLUMNS - 1, 0, common, palette.dimStyle());
    }

    private void drawPlayer(TextFrame frame, BattlePlayer player, GameSnapshot game, List<List<Cell>> pending) {
        int left = leftOf(player);
        BoardPainter board = boardOf(player);
        board.drawPlayfield(frame, game, false);
        board.drawNext(frame, game.engine().nextPiece(), left + BattleInfoPanel.INFO_OFFSET, BattleInfoPanel.NEXT_TITLE_ROW);
        panel.draw(frame, left, label(player), game.level(), game.score(), game.clearedLines(),
                game.doubleScoreClears(), game.engine().board()[0].length, pending);
        drawHelp(frame, player, left);
    }

    private void drawHelp(TextFrame frame, BattlePlayer player, int left) {
        StringBuilder text = new StringBuilder(label(player)).append(' ');
        for (GameCommand command : List.of(GameCommand.MOVE_LEFT, GameCommand.MOVE_RIGHT, GameCommand.ROTATE_CW,
                GameCommand.SOFT_DROP)) {
            text.append(KeyNames.display(keys.keyFor(player, command))).append(' ');
        }
        text.append("· ").append(KeyNames.display(keys.keyFor(player, GameCommand.HARD_DROP)));
        // 바꾼 키 이름이 길어도 상대 쪽 안내를 덮지 않게 자기 영역에서 자른다.
        int limit = (player == BattlePlayer.ONE ? leftOf(BattlePlayer.TWO) - 1 : COLUMNS) - left;
        frame.put(left, HELP_ROW, clip(text.toString(), limit), palette.dimStyle());
    }

    /** 화면 폭 기준으로 width칸까지만 남긴다. 잘렸으면 끝을 ~로 표시한다. */
    static String clip(String text, int width) {
        if (TextFrame.displayWidth(text) <= width) {
            return text;
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String next = kept + new String(Character.toChars(codePoint));
            if (TextFrame.displayWidth(next) > width - 1) {
                break;
            }
            kept.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        return kept + "~";
    }

    /** 남은 시간이 이 초 이하면 경고색으로 표시한다. */
    static final int WARNING_SECONDS = 10;

    /** 초를 m:ss로. */
    static String clock(long seconds) {
        return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    static String label(BattlePlayer player) {
        return player == BattlePlayer.ONE ? "1P" : "2P";
    }
}
