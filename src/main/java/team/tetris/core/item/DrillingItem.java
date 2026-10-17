package team.tetris.core.item;

/**
 * 블록이 고정될 때가 아니라 떨어지는 동안 효과를 내는 아이템 (예: 무게추). 이 아이템이 실린 블록은
 * 엔진에서 다음 규칙으로 움직인다.
 *
 * <ul>
 *   <li>바닥이나 다른 블록에 처음 닿기 전에는 일반 블록처럼 좌우로 움직인다.</li>
 *   <li>처음 닿은 순간부터 좌우 이동이 잠긴다.</li>
 *   <li>그 뒤로 내려갈 때마다 자기가 들어갈 자리의 블록을 지우며 한 칸씩 내려가고, 보드 바닥에 닿으면
 *       고정된다. 한 번에 떨어뜨리기는 바닥까지 한 번에 뚫고 내려간다.</li>
 *   <li>떨어진 칸 수와 지운 칸은 점수로 보고하지 않는다 ({@code EngineStep#dropResult()}가 null).</li>
 * </ul>
 *
 * <p>고정될 때의 {@link #onLock}은 다른 아이템과 같이 호출된다.
 */
public interface DrillingItem extends Item {
}
