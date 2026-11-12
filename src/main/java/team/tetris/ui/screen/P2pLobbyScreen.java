package team.tetris.ui.screen;

import team.tetris.application.network.P2pLobby;
import team.tetris.application.network.MatchMessage.Mode;
import team.tetris.ui.ScreenRouter;
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
    private boolean handedOff;

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
                router.ignoreHeldKeys();
            } else router.showMenu();
            return;
        }
        if (lobby.stage() == P2pLobby.Stage.FAILED) {
            if (key.equals("ENTER")) { lobby.reset(); clientEntry = false; error = ""; router.ignoreHeldKeys(); }
            return;
        }
        if (lobby.stage() == P2pLobby.Stage.CHOICE) {
            if (clientEntry) {
                switch (key) {
                    case "UP", "DOWN" -> {
                        int size = lobby.recent().addresses().size();
                        if (size > 0) {
                            recentCursor = Math.floorMod(recentCursor + (key.equals("UP") ? -1 : 1), size);
                            input = lobby.recent().addresses().get(recentCursor);
                        }
                    }
                    case "BACKSPACE" -> { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); }
                    case "DELETE" -> input = "";
                    case "ENTER" -> {
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
                        else clientEntry = true;
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
                } else if (config.mode() == Mode.TIMED && (key.equals("UP") || key.equals("DOWN"))) {
                    int seconds = (Math.floorMod(config.timeLimitSeconds() / 60 - 1 + (key.equals("UP") ? 1 : -1), 3) + 1) * 60;
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
        if (clientEntry && lobby.stage() == P2pLobby.Stage.CHOICE && input.length() < 15
                && ((value >= '0' && value <= '9') || value == '.')) input += value;
    }

    @Override public void update(long elapsedNanos) {
        lobby.update();
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
        frame.putCentered(1, "P2P 대전", palette.base());
        frame.put(2, 3, "포트: " + P2pLobby.PORT, palette.base());
        wrapped(frame, 5, error.isEmpty() ? lobby.status() : error, palette.base(), 3);
        if (lobby.stage() == P2pLobby.Stage.CHOICE) {
            if (!clientEntry) {
                frame.put(4, 10, (roleCursor == 0 ? "> " : "  ") + "서버", palette.base());
                frame.put(4, 12, (roleCursor == 1 ? "> " : "  ") + "클라이언트", palette.base());
                frame.put(2, 20, "↑↓ 선택 · Enter 확인", palette.base());
            } else {
                frame.put(2, 9, "서버 IPv4: " + input + "_", palette.base());
                frame.put(2, 11, "최근 IP (↑↓ 불러오기)", palette.base());
                for (int i = 0; i < lobby.recent().addresses().size(); i++)
                    frame.put(4, 12 + i, lobby.recent().addresses().get(i), palette.base());
                frame.put(2, 19, "Delete 지우기 · Enter 접속", palette.base());
            }
        } else if (lobby.stage() == P2pLobby.Stage.CONNECTING && lobby.isServer()) {
            frame.put(2, 9, "상대 PC에서 입력할 IPv4:", palette.base());
            var addresses = lobby.addresses();
            if (addresses.isEmpty()) {
                frame.put(2, 11, "LAN 주소를 확인할 수 없습니다", palette.base());
            } else {
                for (int i = addressPage * 5; i < Math.min(addresses.size(), addressPage * 5 + 5); i++)
                    frame.put(4, 11 + i % 5, addresses.get(i), palette.base());
                frame.put(2, 17, "↑↓ 주소 페이지 " + (addressPage + 1) + "/" + ((addresses.size() + 4) / 5), palette.base());
            }
            frame.put(2, 19, "같은 PC 테스트: 127.0.0.1", palette.base());
        } else if (lobby.stage() == P2pLobby.Stage.LOBBY) {
            lobby.configuration().ifPresent(config -> {
                String mode = switch (config.mode()) { case NORMAL -> "일반"; case ITEM -> "아이템"; case TIMED -> "시간제한"; };
                frame.put(2, 9, "모드: " + mode, palette.base());
                frame.put(2, 10, "난이도: " + config.difficulty(), palette.base());
                if (config.mode() == Mode.TIMED) frame.put(2, 11, "제한시간: " + config.timeLimitSeconds() / 60 + "분", palette.base());
            });
            frame.put(2, 13, "나: " + (lobby.localReady() ? "준비 완료" : "대기"), palette.base());
            frame.put(2, 14, "상대: " + (lobby.remoteReady() ? "준비 완료" : "대기"), palette.base());
            if (lobby.isServer()) {
                frame.put(2, 16, "←→ 모드 · ↑↓ 제한시간", palette.base());
                frame.put(2, 17, "설정 변경 시 양쪽 준비 해제", palette.base());
            } else frame.put(2, 17, "서버가 모드와 시간을 선택합니다", palette.base());
            frame.put(2, 19, "Enter 게임 시작", palette.base());
        } else if (lobby.stage() == P2pLobby.Stage.STARTED) {
            frame.putCentered(11, "양쪽 준비 완료", palette.base());
            frame.putCentered(13, "경기 연결 준비됨", palette.base());
        } else if (lobby.stage() == P2pLobby.Stage.FAILED) {
            frame.put(2, 19, "Enter 다시 접속", palette.base());
        }
        frame.put(2, 21, lobby.recent().warning(), palette.base());
        frame.put(2, 23, "Esc 돌아가기", palette.base());
        return frame;
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
