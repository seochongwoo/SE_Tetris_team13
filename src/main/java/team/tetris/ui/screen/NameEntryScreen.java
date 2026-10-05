package team.tetris.ui.screen;

import team.tetris.application.EndGameView;
import team.tetris.application.EndGameView.Stage;
import team.tetris.application.GameResult;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 순위에 들어간 게임이 끝난 뒤 이름을 입력받는 화면. 종료 조율자의 NAME_REQUIRED 단계와,
 * 기록을 확인하지 못한 CHECKING 단계(다시 시도 안내)를 함께 다룬다.
 *
 * <p>아직 기록이 저장되지 않았으므로 창의 X 버튼을 누르면 바로 끄지 않고 한 번 확인한다.
 */
public final class NameEntryScreen implements Screen {

    public static final int MAX_NAME_LENGTH = 12;

    private final ScreenRouter router;
    private final GameResult result;
    private final StringBuilder name = new StringBuilder();
    private EndGameView view;
    private boolean confirmingExit;

    public NameEntryScreen(ScreenRouter router, GameResult result, EndGameView view) {
        this.router = router;
        this.result = result;
        this.view = view;
    }

    public String name() {
        return name.toString();
    }

    public EndGameView view() {
        return view;
    }

    public boolean isConfirmingExit() {
        return confirmingExit;
    }

    @Override
    public void onCharTyped(char character) {
        if (confirmingExit || view.stage() != Stage.NAME_REQUIRED) {
            return;
        }
        if (Character.isISOControl(character) || Character.isSurrogate(character)) {
            return;
        }
        // 게임 중 누르고 있던 Space 등이 반복 입력돼 이름 앞에 공백이 쌓이지 않게 한다.
        if (name.isEmpty() && Character.isWhitespace(character)) {
            return;
        }
        if (name.codePointCount(0, name.length()) < MAX_NAME_LENGTH) {
            name.append(character);
        }
    }

    @Override
    public void onKeyPressed(String key) {
        if (confirmingExit) {
            if (KeyNames.ENTER.equals(key)) {
                router.exit();
            } else if (KeyNames.ESCAPE.equals(key)) {
                confirmingExit = false;
            }
            return;
        }
        if (view.stage() == Stage.CHECKING) {
            if (KeyNames.ENTER.equals(key)) {
                router.retryEnding(result);
            } else if (KeyNames.ESCAPE.equals(key)) {
                router.showMenu();
            }
            return;
        }
        if (KeyNames.BACK_SPACE.equals(key) && !name.isEmpty()) {
            name.setLength(name.offsetByCodePoints(name.length(), -1));
        } else if (KeyNames.ENTER.equals(key)) {
            submit();
        }
    }

    private void submit() {
        EndGameView next = router.submitName(result, name.toString());
        if (next.stage() == Stage.NAME_REQUIRED || next.stage() == Stage.CHECKING) {
            view = next;
        } else {
            router.applyEnding(result, next);
        }
    }

    @Override
    public boolean acceptsTextInput() {
        return true;
    }

    @Override
    public boolean allowsImmediateExit() {
        return false;
    }

    @Override
    public void onExitRequested() {
        confirmingExit = true;
    }

    @Override
    public TextFrame render() {
        ColorPalette palette = router.palette();
        TextFrame frame = TextFrame.blank(palette.base());
        frame.putCentered(3, "GAME OVER", palette.warningStyle());
        frame.putCentered(5, "점수 " + result.score(), palette.base().asBold());

        if (view.stage() == Stage.CHECKING) {
            frame.putCentered(9, "순위 기록을 확인하지 못했습니다", palette.warningStyle());
            frame.putCentered(TextFrame.ROWS - 2, "Enter 다시 시도   Esc 메뉴로", palette.dimStyle());
        } else {
            frame.putCentered(8, "순위에 들었습니다! 이름을 입력하세요", palette.accentStyle());
            int boxLeft = 8;
            int boxWidth = 28;
            frame.box(boxLeft, 10, boxWidth, 3, palette.borderStyle(), palette.base());
            int next = frame.put(boxLeft + 1, 11, name.toString(), palette.base().asBold());
            frame.put(next, 11, "_", palette.accentStyle());
            frame.putCentered(13, "최대 " + MAX_NAME_LENGTH + "자 · 한/영 키로 한글 입력", palette.dimStyle());
            view.nameError().ifPresent(error ->
                    frame.putCentered(15, "이름은 공백만 빼고 1~" + MAX_NAME_LENGTH + "자로 입력하세요", palette.warningStyle()));
            view.storageError().ifPresent(error ->
                    frame.putCentered(16, "저장하지 못했습니다. Enter로 다시 시도하세요", palette.warningStyle()));
            frame.putCentered(TextFrame.ROWS - 2, "Enter 저장   Backspace 지우기", palette.dimStyle());
        }

        if (confirmingExit) {
            frame.box(5, 17, 34, 4, palette.warningStyle(), palette.base());
            frame.putCentered(5, 39, 18, "기록하지 않고 종료할까요?", palette.warningStyle());
            frame.putCentered(5, 39, 19, "Enter 종료   Esc 취소", palette.base());
        }
        return frame;
    }
}
