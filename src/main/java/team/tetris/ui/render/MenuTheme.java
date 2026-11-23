package team.tetris.ui.render;

/**
 * 메뉴 계열 화면(설정, 스코어보드, 이름 입력, P2P 로비)을 시작 메뉴와 같은 도트 그래픽 분위기로 맞춘다.
 * 대성당 배경 위에 위쪽 제목 상자, 가운데 본문 상자, 아래쪽 안내 상자를 겹쳐 둔다.
 */
public final class MenuTheme {

    private MenuTheme() {
    }

    /**
     * 배경과 세 상자를 놓는다. 제목 상자는 0행부터 headerRows줄, 안내 상자는 footerTop행부터 맨 아래까지이고
     * 본문 상자가 그 사이를 채운다. 상자를 서로 붙여 사이로 배경이 조각나 보이지 않게 한다.
     */
    public static void panels(TextFrame frame, long clockNanos, int headerRows, int footerTop) {
        if (headerRows < 1 || footerTop <= headerRows || footerTop >= frame.rows()) {
            throw new IllegalArgumentException("Invalid panel rows: " + headerRows + ", " + footerTop);
        }
        int width = frame.columns() - 2;
        frame.setBackdrop(Backdrop.cathedral(clockNanos));
        frame.addPlate(1, 0, width, headerRows);
        frame.addPlate(1, headerRows, width, footerTop - headerRows);
        frame.addPlate(1, footerTop, width, frame.rows() - footerTop);
    }
}
