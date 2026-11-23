package team.tetris.ui.screen;

import team.tetris.application.model.Difficulty;
import team.tetris.application.network.P2pLobby;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.ui.DisplayNames;
import team.tetris.ui.ScreenRouter;
import team.tetris.ui.input.KeyNames;
import team.tetris.ui.render.Backdrop;
import team.tetris.ui.render.P2pRenderer;
import team.tetris.ui.render.TextFrame;
import team.tetris.ui.render.TextStyle;

/** 키보드만으로 접속·설정·준비가 가능한 Swing 독립 로비. */
public final class P2pLobbyScreen implements Screen {
    private final ScreenRouter router;
    private final P2pLobby lobby;
    private boolean clientEntry;
    private int roleCursor;
    private int recentCursor = -1;
    private int addressPage;
    private String input = "";
    private String error = "";
    private boolean invalidCharacter;
    private boolean handedOff;
    private boolean failureShown;
    private long failureElapsed;
    private long clockNanos;

    public P2pLobbyScreen(ScreenRouter router, P2pLobby lobby) {
        this.router = router;
        this.lobby = lobby;
    }
    public P2pLobby lobby() { return lobby; }
    public String input() { return input; }

    @Override public void onKeyPressed(String key) {
        if (key.equals("ESCAPE")) {
            if (clientEntry || lobby.stage() != P2pLobby.Stage.CHOICE) {
                lobby.reset(); clientEntry = false; error = ""; addressPage = 0;
                failureShown = false; failureElapsed = 0;
                router.ignoreHeldKeys();
            } else router.showMenu();
            return;
        }
        if (lobby.stage() == P2pLobby.Stage.FAILED) {
            if (key.equals("ENTER")) {
                lobby.reset(); clientEntry = false; error = "";
                failureShown = false; failureElapsed = 0; router.ignoreHeldKeys();
            }
            return;
        }
        if (lobby.stage() == P2pLobby.Stage.CHOICE) {
            if (clientEntry) {
                switch (key) {
                    case "UP", "DOWN" -> {
                        int size = lobby.recent().addresses().size();
                        if (size > 0) {
                            recentCursor = Math.floorMod(recentCursor + (key.equals("UP") ? -1 : 1), size);
                            invalidCharacter = false;
                            input = lobby.recent().addresses().get(recentCursor);
                        }
                    }
                    case KeyNames.BACK_SPACE -> { invalidCharacter = false; if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }
                    case "DELETE" -> { input = ""; invalidCharacter = false; }
                    case "ENTER" -> {
                        invalidCharacter = false;
                        try { lobby.connect(input); clientEntry = false; error = ""; router.ignoreHeldKeys(); }
                        catch (IllegalArgumentException invalid) { error = invalid.getMessage(); }
                    }
                    default -> { }
                }
            } else {
                switch (key) {
                    case "UP", "DOWN" -> roleCursor = 1 - roleCursor;
                    case "ENTER" -> {
                        if (roleCursor == 0) lobby.host();
                        else { clientEntry = true; invalidCharacter = false; }
                        router.ignoreHeldKeys();
                    }
                    default -> { }
                }
            }
        } else if (lobby.stage() == P2pLobby.Stage.LOBBY) {
            if (key.equals("ENTER")) lobby.ready();
            else if (lobby.isServer() && lobby.configuration().isPresent()) {
                var config = lobby.configuration().orElseThrow();
                if (key.equals("LEFT") || key.equals("RIGHT")) {
                    Mode mode = Mode.values()[Math.floorMod(config.mode().ordinal() + (key.equals("LEFT") ? -1 : 1), 3)];
                    lobby.configure(mode, mode == Mode.TIMED ? 120 : 0);
                } else if (key.equals("UP") || key.equals("DOWN")) {
                    Difficulty[] levels = Difficulty.values();
                    int index = Math.floorMod(config.difficulty().ordinal() + (key.equals("UP") ? 1 : -1), levels.length);
                    lobby.configure(config.mode(), levels[index], config.timeLimitSeconds());
                } else if (config.mode() == Mode.TIMED && switch (key) {
                    case "COMMA", "LESS", "PERIOD", "GREATER" -> true;
                    default -> false;
                }) {
                    int seconds = (Math.floorMod(config.timeLimitSeconds() / 60 - 1 + (key.equals("COMMA") || key.equals("LESS") ? -1 : 1), 3) + 1) * 60;
                    lobby.configure(Mode.TIMED, seconds);
                }
            }
        } else if (lobby.stage() == P2pLobby.Stage.CONNECTING && lobby.isServer()) {
            int pages = Math.max(1, (lobby.addresses().size() + 4) / 5);
            if (key.equals("DOWN")) addressPage = (addressPage + 1) % pages;
            if (key.equals("UP")) addressPage = Math.floorMod(addressPage - 1, pages);
        }
    }

    @Override public void onCharTyped(char value) {
        if (!clientEntry || lobby.stage() != P2pLobby.Stage.CHOICE || Character.isISOControl(value)) return;
        invalidCharacter = (value < '0' || value > '9') && value != '.';
        if (!invalidCharacter && input.length() < 15) input += value;
    }

    @Override public void update(long elapsedNanos) {
        clockNanos += elapsedNanos;
        lobby.update();
        if (lobby.stage() == P2pLobby.Stage.FAILED) {
            if (failureShown) failureElapsed += Math.min(elapsedNanos, 2_000_000_000L);
            else { failureShown = true; failureElapsed = 0; router.ignoreHeldKeys(); }
            if (failureElapsed >= 2_000_000_000L) {
                lobby.reset(); clientEntry = false; error = ""; addressPage = 0;
                router.ignoreHeldKeys();
            }
        } else { failureShown = false; failureElapsed = 0; }
        handoffStartedMatch().ifPresent(started -> router.startP2pMatch(lobby, started));
    }
    /** 경기 화면으로 전환하기 직전에 호출. 연결 정리는 이후 경기 화면의 책임이다. */
    public java.util.Optional<P2pLobby.StartedMatch> handoffStartedMatch() {
        var started = lobby.takeStartedMatch();
        if (started.isPresent()) handedOff = true;
        return started;
    }

    @Override public void onLeave() { if (!handedOff) lobby.close(); }

    @Override public TextFrame render() {
        var palette = router.palette();
        var frame = TextFrame.blank(palette.base());
        frame.setBackdrop(Backdrop.cathedral(clockNanos));
        frame.addPlate(1, 0, TextFrame.COLUMNS - 2, 8);
        boolean hasWarning = !lobby.recent().warning().isEmpty();
        int footerTop = hasWarning ? 20 : 21;
        // 본문 상자를 아래 안내 상자에 바로 붙여, 사이로 배경 그림이 조각나 보이지 않게 한다.
        int bodyRows = footerTop - 8;
        frame.putCentered(1, "P2P 대전 로비", palette.accentStyle());
        frame.put(2, 3, "포트: " + P2pLobby.PORT, palette.dimStyle());
        String prompt = clientEntry ? "접속할 서버의 IPv4 주소를 입력하세요" : lobby.status();
        wrapped(frame, 4, error.isEmpty() ? prompt : error,
                !error.isEmpty() || lobby.stage() == P2pLobby.Stage.FAILED ? palette.warningStyle() : palette.base(), 3);
        if (lobby.stage() == P2pLobby.Stage.CHOICE) {
            if (!clientEntry) {
                frame.addPlate(11, 9, 22, 5);
                for (int i = 0; i < 2; i++) {
                    int row = 10 + 2 * i;
                    var style = i == roleCursor ? palette.highlightStyle() : palette.base();
                    if (i == roleCursor) {
                        frame.fill(12, row, 20, 1, style);
                        frame.put(13, row, "►", style);
                    }
                    frame.putCentered(row, i == 0 ? "서버" : "클라이언트", style);
                }
            } else {
                frame.addPlate(1, 8, TextFrame.COLUMNS - 2, bodyRows);
                frame.put(2, 9, "서버 IPv4: " + input + "_", palette.base());
                if (invalidCharacter) frame.put(2, 10, "숫자와 점(.)만 입력할 수 있습니다", palette.warningStyle());
                frame.put(2, 11, "최근 IP (↑↓ 불러오기)", palette.base());
                for (int i = 0; i < lobby.recent().addresses().size(); i++)
                    frame.put(4, 12 + i, lobby.recent().addresses().get(i), palette.base());
                frame.put(2, 18, "Delete 지우기 · Enter 접속", palette.base());
            }
        } else if (lobby.stage() == P2pLobby.Stage.CONNECTING && lobby.isServer()) {
            frame.addPlate(1, 8, TextFrame.COLUMNS - 2, bodyRows);
            frame.put(2, 9, "상대 PC에서 입력할 IPv4:", palette.base());
            var addresses = lobby.addresses();
            if (addresses.isEmpty()) {
                frame.put(2, 11, "LAN 주소를 확인할 수 없습니다", palette.base());
            } else {
                for (int i = addressPage * 5; i < Math.min(addresses.size(), addressPage * 5 + 5); i++)
                    frame.put(4, 11 + i % 5, addresses.get(i), palette.base());
                frame.put(2, 17, "↑↓ 주소 페이지 " + (addressPage + 1) + "/" + ((addresses.size() + 4) / 5), palette.base());
            }
            frame.put(2, 18, "같은 PC 테스트: 127.0.0.1", palette.base());
        } else if (lobby.stage() == P2pLobby.Stage.LOBBY) {
            frame.addPlate(1, 8, TextFrame.COLUMNS - 2, bodyRows);
            boolean server = lobby.isServer();
            lobby.configuration().ifPresent(config -> {
                optionRow(frame, 9, "모드", MODE_NAMES, config.mode().ordinal(), server);
                optionRow(frame, 10, "난이도", DIFFICULTY_NAMES, config.difficulty().ordinal(), server);
                if (config.mode() == Mode.TIMED) {
                    optionRow(frame, 11, "제한시간", TIME_NAMES, config.timeLimitSeconds() / 60 - 1, server);
                }
            });
            frame.put(2, 13, "나: " + (lobby.localReady() ? "준비 완료" : "대기"), lobby.localReady() ? palette.accentStyle() : palette.base());
            frame.put(2, 14, "상대: " + (lobby.remoteReady() ? "준비 완료" : "대기"), lobby.remoteReady() ? palette.accentStyle() : palette.base());
            if (server) {
                boolean timed = lobby.configuration().filter(config -> config.mode() == Mode.TIMED).isPresent();
                frame.put(2, 16, "←→ 모드 · ↑↓ 난이도" + (timed ? " · < > 제한시간" : ""), palette.dimStyle());
                frame.put(2, 17, "설정을 바꾸면 양쪽 준비가 해제됩니다", palette.dimStyle());
            } else frame.put(2, 17, "서버가 경기 설정을 선택합니다", palette.dimStyle());
            frame.putCentered(18, lobby.localReady() ? "상대가 게임 시작을 누르면 시작합니다" : "Enter 게임 시작",
                    lobby.localReady() ? palette.base() : palette.accentStyle());
        } else if (lobby.stage() == P2pLobby.Stage.STARTED) {
            frame.addPlate(8, 10, 28, 5);
            frame.putCentered(11, "양쪽 준비 완료", palette.base());
            frame.putCentered(13, "경기 연결 준비됨", palette.base());
        } else if (lobby.stage() == P2pLobby.Stage.FAILED) {
            frame.addPlate(1, 16, TextFrame.COLUMNS - 2, 4);
            frame.putCentered(17, "잠시 후 P2P 첫 화면으로", palette.base());
            frame.putCentered(18, "Enter 바로 돌아가기", palette.base());
        }
        frame.addPlate(1, footerTop, TextFrame.COLUMNS - 2, TextFrame.ROWS - footerTop);
        if (hasWarning) {
            frame.put(2, 21, lobby.recent().warning(), palette.warningStyle());
        }
        String hint = lobby.stage() == P2pLobby.Stage.CHOICE && !clientEntry
                ? "↑↓ 선택 · Enter 확인 · Esc 돌아가기"
                : "Esc 돌아가기";
        // 지연 표시가 있으면 안내는 왼쪽, 지연은 오른쪽에 둔다. 바깥 테두리 위에 겹치지 않게 같은 줄에 넣는다.
        if (P2pRenderer.hasLatency(lobby.roundTripNanos(), lobby.lagging())) {
            frame.put(3, 22, hint, palette.dimStyle());
            P2pRenderer.drawLatency(frame, palette, lobby.roundTripNanos(), lobby.lagging(), 22, TextFrame.COLUMNS - 3);
        } else {
            frame.putCentered(22, hint, palette.dimStyle());
        }
        return frame;
    }

    private static final java.util.List<String> MODE_NAMES = java.util.List.of("일반", "아이템", "시간제한");
    private static final java.util.List<String> DIFFICULTY_NAMES = java.util.Arrays.stream(Difficulty.values())
            .map(DisplayNames::of).toList();
    private static final java.util.List<String> TIME_NAMES = java.util.List.of("1분", "2분", "3분");
    /** 선택지 이름이 시작하는 열. 가장 긴 이름표("제한시간")보다 오른쪽. */
    private static final int OPTION_LEFT = 13;

    /** 설정 화면처럼 선택지를 모두 보여 주고 현재 값을 [ ]로 강조한다. 클라이언트는 바꿀 수 없어 흐리게 둔다. */
    private void optionRow(TextFrame frame, int row, String label, java.util.List<String> names, int selected,
                           boolean editable) {
        var palette = router.palette();
        frame.put(2, row, label, palette.base());
        int col = OPTION_LEFT;
        for (int i = 0; i < names.size(); i++) {
            boolean chosen = i == selected;
            String text = chosen ? "[" + names.get(i) + "]" : " " + names.get(i) + " ";
            TextStyle style = chosen ? (editable ? palette.accentStyle() : palette.base().asBold()) : palette.dimStyle();
            col = frame.put(col, row, text, style) + 1;
        }
    }

    private static void wrapped(TextFrame frame, int row, String text, TextStyle style, int rows) {
        StringBuilder line = new StringBuilder();
        int end = row + rows;
        for (char character : text.toCharArray()) {
            if (TextFrame.displayWidth(line.toString() + character) > TextFrame.COLUMNS - 4) {
                frame.put(2, row++, line.toString(), style);
                line.setLength(0);
                if (row == end) return;
            }
            line.append(character);
        }
        frame.put(2, row, line.toString(), style);
    }
}
