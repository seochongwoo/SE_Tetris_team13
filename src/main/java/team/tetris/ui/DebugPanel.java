package team.tetris.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.*;
import team.tetris.application.GameStatus;
import team.tetris.application.debug.DebugItem;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.debug.DebugSession;
import team.tetris.application.model.Difficulty;
import team.tetris.core.TetrominoType;

/** 보드와 스탯을 편집하는 디버그 패널. 숫자 편집 중에는 게임 입력과 중력을 멈춘다. */
final class DebugPanel extends JPanel {
    private final ScreenRouter router;
    private final Runnable focusGame;
    private final JSpinner filledRows = number("filledRows", 0, 18);
    private final JSpinner holesPerRow = number("holesPerRow", 1, 10);
    private final JSpinner clearedLines = number("clearedLines", 0, DebugSetup.MAX_LINES);
    private final JComboBox<DebugSetup.HolePattern> pattern = new JComboBox<>(DebugSetup.HolePattern.values());
    private final JComboBox<DebugSetup.FillMode> fillMode = new JComboBox<>(DebugSetup.FillMode.values());
    private final JComboBox<Difficulty> difficulty = new JComboBox<>(Difficulty.values());
    private final JCheckBox repeatSeed = new JCheckBox("같은 랜덤 결과 유지", true);
    private final JComboBox<TetrominoType> pieces = new JComboBox<>(TetrominoType.values());
    private final JComboBox<DebugItem> items = new JComboBox<>(DebugItem.all().toArray(DebugItem[]::new));
    private final JCheckBox frozen = new JCheckBox("자동 낙하 정지 (이동·회전 가능)", true);
    private final JTextArea state = readOnlyText(8);
    private final JTextArea logPath = readOnlyText(3);
    private final JLabel error = new JLabel(" ");
    private final List<Component> playingControls = new ArrayList<>();
    private final JButton restart;
    private final JButton previousSetup;
    private final JButton undoPlacement;
    private final JButton stats;
    private DebugSession displayedSession;

    DebugPanel(ScreenRouter router, Runnable focusGame) {
        this.router = router;
        this.focusGame = focusGame;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setPreferredSize(new Dimension(300, 870));
        add(new JLabel("DEBUG · 보드 / 스탯 편집"));
        label("보드 (위 두 줄은 생성 공간)");
        row("아래부터 채울 줄 수", filledRows);
        row("줄마다 비울 칸 수", holesPerRow);
        pattern.setName("pattern");
        pattern.setSelectedItem(DebugSetup.HolePattern.RANDOM);
        row("빈칸 배치", pattern);
        fillMode.setName("fillMode");
        row("채울 블록", fillMode);
        repeatSeed.setName("repeatSeed");
        control(repeatSeed);
        label("스탯");
        row("누적 삭제 줄 수", clearedLines);
        difficulty.setSelectedItem(router.settings().difficulty());
        difficulty.setName("difficulty");
        row("난이도", difficulty);
        button("보드·스탯 적용", () -> router.startDebugSetup(readSetup(false)));
        button("보드 비우기", () -> router.startDebugSetup(readSetup(true)));
        stats = button("스탯만 적용", () -> router.debugSession().ifPresent(debug -> {
            int lines = value(clearedLines);
            router.freezeDebugInput();
            debug.applyStats(lines, (Difficulty) difficulty.getSelectedItem());
        }));
        restart = button("설정한 상태로 되돌리기", () -> router.debugSession().ifPresent(debug ->
                router.startDebugSetup(debug.setup())));
        previousSetup = button("직전 설정으로 되돌리기", router::restorePreviousDebugSetup);
        undoPlacement = button("직전 배치로 되돌리기", router::undoDebugPlacement);
        error.setForeground(java.awt.Color.RED);
        error.setAlignmentX(LEFT_ALIGNMENT);
        add(error);
        label("진행 제어");
        control(frozen);
        playingControls.add(frozen);
        frozen.addActionListener(event -> act(() -> router.debugSession().ifPresent(
                debug -> debug.setFrozen(frozen.isSelected()))));
        playingControls.add(button("중력 한 단계 (낙하 / 고정)", () ->
                router.debugSession().ifPresent(DebugSession::stepGravity)));
        label("다음 블록 / 아이템");
        pieces.setName("pieces");
        row("블록", pieces);
        items.setName("items");
        row("아이템", items);
        playingControls.add(pieces);
        playingControls.add(items);
        playingControls.add(button("다음 블록에 적용", () -> router.debugSession().ifPresent(debug ->
                debug.replaceNext((TetrominoType) pieces.getSelectedItem(),
                        (DebugItem) items.getSelectedItem()))));
        label("아이템 '없음' 적용 시 기존 아이템 제거");
        label("전용 모양 아이템은 선택한 블록을 대체합니다.");
        label("P 일시정지 중에는 재개 후 조작하세요.");
        add(state);
        add(logPath);
        add(Box.createVerticalGlue());
        button("메뉴로", router::showMenu);
        for (JSpinner spinner : List.of(filledRows, holesPerRow, clearedLines)) {
            var field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            field.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { router.freezeDebugInput(); }
            });
        }
        refresh();
    }

    private DebugSetup readSetup(boolean empty) {
        long seed = repeatSeed.isSelected() ? router.debugSession().map(debug -> debug.setup().seed()).orElse(13L)
                : ThreadLocalRandom.current().nextLong();
        return new DebugSetup(empty ? 0 : value(filledRows), value(holesPerRow),
                (DebugSetup.HolePattern) pattern.getSelectedItem(),
                (DebugSetup.FillMode) fillMode.getSelectedItem(), value(clearedLines),
                (Difficulty) difficulty.getSelectedItem(), seed);
    }

    void refresh() {
        DebugSession debug = router.debugSession().orElse(null);
        var snapshot = debug == null ? null : debug.snapshot();
        boolean running = snapshot != null && snapshot.status() == GameStatus.RUNNING;
        playingControls.forEach(control -> control.setEnabled(running));
        restart.setEnabled(debug != null);
        previousSetup.setEnabled(router.canRestorePreviousDebugSetup());
        undoPlacement.setEnabled(router.canUndoDebugPlacement());
        stats.setEnabled(debug != null && debug.result().isEmpty());
        if (debug == null) {
            displayedSession = null;
            setText(state, "보드와 스탯을 설정한 뒤 적용하세요.");
            setText(logPath, "로그는 콘솔과 debug/debug.log에 기록됩니다.");
            return;
        }
        if (displayedSession != debug) {
            displayedSession = debug;
            filledRows.setValue(debug.setup().filledRows());
            holesPerRow.setValue(debug.setup().holesPerRow());
            pattern.setSelectedItem(debug.setup().pattern());
            fillMode.setSelectedItem(debug.setup().fillMode());
            clearedLines.setValue(snapshot.clearedLines());
            difficulty.setSelectedItem(debug.difficulty());
        }
        frozen.setSelected(debug.frozen());
        var active = snapshot.engine().activePiece();
        String position = active == null ? "없음" : active.shape() + " x=" + active.origin().x()
                + " y=" + active.origin().y() + " 회전=" + active.rotation();
        setText(state, "\n상태: " + snapshot.status() + " / " + debug.difficulty()
                + "\n현재: " + position
                + "\n점수: " + snapshot.score() + " / 줄: " + snapshot.clearedLines()
                + "\n레벨: " + snapshot.level() + " / 중력: " + snapshot.gravityIntervalNanos() / 1_000_000 + "ms"
                + "\n점수 2배 남은 횟수: " + snapshot.doubleScoreClears()
                + "\n시드: " + debug.setup().seed());
        setText(logPath, debug.logDescription());
    }

    private static JSpinner number(String name, int initial, int maximum) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, 0, maximum, 1));
        spinner.setName(name);
        return spinner;
    }

    private static int value(JSpinner spinner) {
        try {
            spinner.commitEdit();
            return ((Number) spinner.getValue()).intValue();
        } catch (ParseException invalid) {
            throw new IllegalArgumentException("허용 범위 안의 숫자를 입력하세요.");
        }
    }

    private static JTextArea readOnlyText(int rows) {
        JTextArea area = new JTextArea(rows, 22);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * 20));
        return area;
    }

    private static void setText(JTextArea area, String text) {
        if (!area.getText().equals(text)) area.setText(text);
    }

    private void label(String text) {
        add(Box.createVerticalStrut(8));
        add(new JLabel(text));
    }

    private void row(String label, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        if (!(component instanceof JSpinner)) component.setFocusable(false);
        control(row);
    }

    private void control(JComponent component) {
        component.setFocusable(false);
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        add(component);
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        control(button);
        button.addActionListener(event -> act(action));
        return button;
    }

    private void act(Runnable action) {
        try {
            action.run();
            error.setText(" ");
            refresh();
            focusGame.run();
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            error.setText(invalid.getMessage());
        }
    }
}
