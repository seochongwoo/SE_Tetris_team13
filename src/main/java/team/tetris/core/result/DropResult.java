package team.tetris.core.result;

/**
 * 이번 액션/틱으로 블록이 실제로 몇 칸 아래로 내려갔는지에 대한 사실만 담는다.
 * 점수 계산(칸당 몇 점 등)은 여기서 하지 않는다 - application(ScorePolicy)이 이 값을 받아
 * 알아서 점수로 환산한다.
 *
 * <p>내려간 칸이 없거나(잠김으로 이어지지 않은 이동/회전 등) 애초에 하강 시도가 아니었던
 * 경우에는 {@link EngineStep#dropResult()}가 null이지, 이 레코드가 만들어지지 않는다.
 */
public record DropResult(int cellsDropped) {
}
