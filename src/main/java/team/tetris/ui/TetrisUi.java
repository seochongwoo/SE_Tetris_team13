package team.tetris.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import team.tetris.application.ApplicationContext;
import team.tetris.application.model.LoadResult;
import team.tetris.application.model.Settings;
import team.tetris.application.model.Settings.ScreenSize;
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
                settings -> fitToScreen(window, panel, settings.screenSize()));
        loop[0] = new GameLoop(NanoClock.system(), elapsed -> {
            router.update(elapsed);
            if (!router.hasExited()) {
                // 이름 입력 화면에서만 입력기를 켠다 (화면은 키 입력이나 게임 종료로 바뀌므로 매 프레임 맞춘다).
                panel.setTextInput(router.acceptsTextInput());
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
        window.pack(); // 창 테두리(제목 표시줄) 크기를 알아야 화면에 맞출 수 있다.
        fitToScreen(window, panel, initial.screenSize());
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        panel.requestFocusInWindow();
        loop[0].start();
    }

    /**
     * 화면 크기 설정을 적용하되, 창이 작업 표시줄을 뺀 화면보다 크면 글자 크기를 줄여 맞추고 창을 화면
     * 안으로 옮긴다. 창 크기는 바꿀 수 없게 해 두었으므로 넘치는 부분은 볼 방법이 없기 때문이다.
     */
    private static void fitToScreen(JFrame window, TextFramePanel panel, ScreenSize size) {
        Rectangle usable = usableArea(window.getGraphicsConfiguration());
        Insets border = window.getInsets();
        panel.setScreenSize(size, new Dimension(
                usable.width - border.left - border.right,
                usable.height - border.top - border.bottom));
        window.pack();
        int x = Math.max(usable.x, Math.min(window.getX(), usable.x + usable.width - window.getWidth()));
        int y = Math.max(usable.y, Math.min(window.getY(), usable.y + usable.height - window.getHeight()));
        window.setLocation(x, y);
    }

    private static Rectangle usableArea(GraphicsConfiguration screen) {
        Rectangle bounds = screen.getBounds();
        Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        return new Rectangle(bounds.x + taskbar.left, bounds.y + taskbar.top,
                bounds.width - taskbar.left - taskbar.right, bounds.height - taskbar.top - taskbar.bottom);
    }

    /**
     * 창·작업 표시줄 아이콘. 배포 실행 파일의 {@code icon.ico}와 같은 그림인 {@code icon.png}(256px)를
     * 읽어 여러 크기로 넘긴다. 읽지 못하면 기본 아이콘을 그대로 쓴다.
     */
    private static List<Image> icons() {
        try (InputStream in = TetrisUi.class.getResourceAsStream("/icon.png")) {
            BufferedImage source = in == null ? null : ImageIO.read(in);
            if (source == null) {
                return List.of();
            }
            return List.of(scaled(source, 16), scaled(source, 32), scaled(source, 64), source);
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Image scaled(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return image;
    }
}
