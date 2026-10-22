package team.tetris.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import team.tetris.application.port.DebugLogSink;

/** 동작별 로그를 콘솔과 UTF-8 파일에 기록한다. 매 append에서 닫아 종료 시에도 로그를 보존한다. */
public final class ConsoleFileDebugLog implements DebugLogSink {
    private final Path file;
    private String error;

    public ConsoleFileDebugLog(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override public void append(String entry) {
        System.out.print(entry);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            error = null;
        } catch (IOException failure) {
            String message = "로그 파일 저장 실패: " + failure.getMessage();
            if (!message.equals(error)) System.err.println(message);
            error = message;
        }
    }

    @Override public String description() {
        return error == null ? "로그: " + file : error + " (콘솔 출력 유지)";
    }
}
