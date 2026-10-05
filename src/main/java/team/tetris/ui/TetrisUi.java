package team.tetris.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import team.tetris.application.ApplicationContext;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;
import team.tetris.application.port.GameUi;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.input.NanoClock;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextFramePanel;
import team.tetris.ui.render.palette.ColorPalette;

/**
 * 실제 UI 진입점. {@code META-INF/services/team.tetris.application.port.GameUi}에 등록되어
 * {@code TetrisApplication.main}이 ServiceLoader로 찾는다.
 *
 * <p>Swing 창 하나에 {@link TextFramePanel}을 올리고, 키 이벤트는 {@link ScreenRouter}로, 프레임
 * 갱신은 {@link GameLoop}으로 연결하는 일만 한다. 화면 로직은 전부 Swing을 모르는 쪽에 있다.
 */
public final class TetrisUi implements GameUi {

    static final String TITLE = "SE Tetris - Team 13";

    /** ServiceLoader가 생성할 수 있도록 public 기본 생성자를 둔다. */
    public TetrisUi() {
    }

    @Override
    public void open(ApplicationContext application) {
        Objects.requireNonNull(application, "application");
        SwingUtilities.invokeLater(() -> createWindow(application));
    }

    private static void createWindow(ApplicationContext application) {
        LoadResult<Settings> loaded = application.settings().loadOrDefault();
        Settings initial = loaded.value();
        JFrame window = new JFrame(TITLE);
        TextFramePanel panel = new TextFramePanel(initial.screenSize(),
                TextFrame.blank(ColorPalette.of(initial.colorBlindMode()).base()));

        GameLoop[] loop = new GameLoop[1];
        ScreenRouter router = new ScreenRouter(application, loaded,
                () -> {
                    loop[0].stop();
                    window.dispose();
                },
                settings -> {
                    panel.setScreenSize(settings.screenSize());
                    window.pack();
                });
        loop[0] = new GameLoop(NanoClock.system(), elapsed -> {
            router.update(elapsed);
            if (!router.hasExited()) {
                panel.show(router.render());
            }
        });

        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                String key = KeyNames.nameOf(event.getKeyCode());
                if (key != null) {
                    router.keyPressed(key);
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                String key = KeyNames.nameOf(event.getKeyCode());
                if (key != null) {
                    router.keyReleased(key);
                }
            }

            @Override
            public void keyTyped(KeyEvent event) {
                char character = event.getKeyChar();
                if (character != KeyEvent.CHAR_UNDEFINED) {
                    router.charTyped(character);
                }
            }
        });
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                router.requestExit();
            }

            @Override
            public void windowDeactivated(WindowEvent event) {
                router.focusLost();
            }
        });

        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        window.setIconImages(icons());
        window.setResizable(false);
        window.add(panel);
        panel.show(router.render());
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        panel.requestFocusInWindow();
        loop[0].start();
    }

    /** 창·작업 표시줄 아이콘: T 블록을 그린 그림. */
    private static List<Image> icons() {
        return List.of(icon(16), icon(32), icon(64));
    }

    private static Image icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);
            int cell = size / 4;
            int left = (size - cell * 3) / 2;
            int top = (size - cell * 2) / 2;
            int[][] cells = {{1, 0}, {0, 1}, {1, 1}, {2, 1}};
            for (int[] c : cells) {
                int x = left + c[0] * cell;
                int y = top + c[1] * cell;
                g.setColor(new Color(255, 0, 255));
                g.fillRect(x, y, cell, cell);
                g.setColor(Color.WHITE);
                g.drawRect(x, y, cell - 1, cell - 1);
            }
        } finally {
            g.dispose();
        }
        return image;
    }
}
