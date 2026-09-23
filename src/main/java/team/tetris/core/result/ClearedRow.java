package team.tetris.core.result;

/**
 * 라인 클리어로 지워진 한 행. rowIndex는 지워지기 직전, 보드 상에서의 행 번호(0 = 맨 위)다.
 */
public record ClearedRow(int rowIndex) {
}
