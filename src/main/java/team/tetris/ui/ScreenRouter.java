package team.tetris.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import team.tetris.application.ApplicationContext;
import team.tetris.application.EndGameView;
import team.tetris.application.GameResult;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;
import team.tetris.application.port.StorageException;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.palette.ColorPalette;
import team.tetris.ui.screen.GameScreen;
import team.tetris.ui.screen.MenuScreen;
import team.tetris.ui.screen.MenuScreen.MenuItem;
import team.tetris.ui.screen.NameEntryScreen;
import team.tetris.ui.screen.ScoreboardScreen;
import team.tetris.ui.screen.Screen;
import team.tetris.ui.screen.SettingsScreen;

/**
 * 화면 전환과 application 서비스 호출을 한곳에 모은다. 화면들은 다음 화면을 직접 만들지 않고 이
 * 라우터에 요청한다. Swing을 모르므로 가짜 ApplicationContext로 화면 흐름 전체를 테스트할 수 있다.
 *
 * <p>게임 종료 뒤의 흐름은 {@code EndGameCoordinator}가 돌려주는 단계를 그대로 따른다:
 * RETURN_MENU → 메뉴, NAME_REQUIRED/CHECKING → 이름 입력, SHOW_SCOREBOARD → 순위표.
 */
public final class ScreenRouter {

    private final ApplicationContext application;
    private final Runnable exitAction;
    private final Consumer<Settings> settingsListener;
    private Settings settings;
    private String notice;
    private Screen current;
    private boolean exited;

    /**
     * @param exitAction       프로그램을 끌 때 한 번 실행 (창 닫기)
     * @param settingsListener 설정이 바뀌어 저장된 뒤 호출 (창 크기 다시 맞추기)
     */
    public ScreenRouter(ApplicationContext application, LoadResult<Settings> initialSettings,
                        Runnable exitAction, Consumer<Settings> settingsListener) {
        this.application = Objects.requireNonNull(application, "application");
        this.exitAction = Objects.requireNonNull(exitAction, "exitAction");
        this.settingsListener = Objects.requireNonNull(settingsListener, "settingsListener");
        this.settings = initialSettings.value();
        this.notice = initialSettings.error().isPresent() ? "설정을 읽지 못해 기본값을 사용합니다" : null;
        showMenu();
    }

    public Screen current() {
        return current;
    }

    public Settings settings() {
        return settings;
    }

    public ColorPalette palette() {
        return ColorPalette.of(settings.colorBlindMode());
    }

    /** 메뉴 화면에 보여줄 안내 (예: 설정 파일을 읽지 못함). */
    public Optional<String> notice() {
        return Optional.ofNullable(notice);
    }

    public boolean hasExited() {
        return exited;
    }

    // ---- 화면 전환 ----

    public void showMenu() {
        show(new MenuScreen(this, menuItems()));
    }

    /** 시작 메뉴 항목. 메뉴를 추가하려면 여기에 한 줄을 더한다. */
    public List<MenuItem> menuItems() {
        return List.of(
                new MenuItem("게임 시작", this::startGame),
                new MenuItem("설정", this::showSettings),
                new MenuItem("스코어보드", this::showScoreboard),
                new MenuItem("종료", this::exit));
    }

    public void startGame() {
        show(new GameScreen(this, application.newGame()));
    }

    public void showSettings() {
        show(new SettingsScreen(this));
    }

    public void showScoreboard() {
        show(ScoreboardScreen.fromMenu(this, application.scores().loadOrEmpty()));
    }

    /** 게임 화면이 끝난 판의 결과를 넘긴다. */
    public void finishGame(GameResult result) {
        applyEnding(result, application.endings().begin(result));
    }

    public void retryEnding(GameResult result) {
        finishGame(result);
    }

    public EndGameView submitName(GameResult result, String name) {
        return application.endings().submitName(result.gameId(), name);
    }

    public void applyEnding(GameResult result, EndGameView view) {
        switch (view.stage()) {
            case RETURN_MENU -> showMenu();
            case NAME_REQUIRED, CHECKING -> show(new NameEntryScreen(this, result, view));
            case SHOW_SCOREBOARD -> show(ScoreboardScreen.afterGame(this, result, view));
        }
    }

    // ---- 설정·기록 ----

    /** 저장에 성공했을 때만 새 설정을 적용한다. */
    public void updateSettings(Settings next) throws StorageException {
        application.settings().update(next);
        applySettings(next);
    }

    public void resetSettings() throws StorageException {
        application.settings().reset();
        applySettings(application.settings().get());
    }

    public void clearScores() throws StorageException {
        application.scores().clear();
    }

    private void applySettings(Settings next) {
        settings = next;
        notice = null;
        settingsListener.accept(next);
    }

    // ---- 루프·입력 전달 ----

    public void update(long elapsedNanos) {
        current.update(elapsedNanos);
    }

    public TextFrame render() {
        return current.render();
    }

    public void keyPressed(String key) {
        current.onKeyPressed(key);
    }

    public void keyReleased(String key) {
        current.onKeyReleased(key);
    }

    public void charTyped(char character) {
        current.onCharTyped(character);
    }

    public void focusLost() {
        current.onFocusLost();
    }

    // ---- 종료 ----

    /** 창의 X 버튼. 기록이 저장되지 않은 화면이면 확인을 먼저 받는다. */
    public void requestExit() {
        if (current.allowsImmediateExit()) {
            exit();
        } else {
            current.onExitRequested();
        }
    }

    public void exit() {
        if (exited) {
            return;
        }
        exited = true;
        exitAction.run();
    }

    private void show(Screen next) {
        current = next;
        next.onEnter();
    }
}
