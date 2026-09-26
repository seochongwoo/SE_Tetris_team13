package team.tetris.application.port;

import team.tetris.application.model.ScoreStore;

public interface ScoreRepository {
    /** 파일 부재 시 빈 기록 반환. 읽기 오류는 별도 전달. */
    ScoreStore load() throws StorageException;
    /** 기록과 등록 이력을 하나의 단위로 저장. 실패 시 기존 값 보존. */
    void save(ScoreStore store) throws StorageException;
}
