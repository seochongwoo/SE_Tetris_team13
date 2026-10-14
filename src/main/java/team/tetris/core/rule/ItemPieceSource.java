package team.tetris.core.rule;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import team.tetris.core.Piece;
import team.tetris.core.item.ItemKind;

/**
 * 아이템 모드의 블록 공급자. 일반 공급자(base)를 감싸, 지운 줄이 누적 10줄을 넘을 때마다 그다음
 * 미리보기에 새로 나타나는 블록에 아이템을 싣는다 (요구사항: 10줄이 삭제되면 다음 블록 미리보기에
 * 새로 나타나는 블록은 반드시 아이템이거나 아이템을 포함).
 *
 * <p>줄 수는 게임 전체 누적으로 센다. 한 번에 여러 기준을 넘으면 넘은 수만큼 이어서 아이템이 나온다.
 * 아이템은 kinds에서 균등 확률로 고른다. kinds가 비어 있으면 아이템 없이 일반 블록만 나온다.
 */
public final class ItemPieceSource implements PieceSource {

    /** 아이템 하나가 나오는 데 필요한 누적 삭제 줄 수. */
    public static final int LINES_PER_ITEM = 10;

    private final PieceSource base;
    private final List<ItemKind> kinds;
    private final RandomGenerator random;
    private Piece upcoming;
    private int totalLines;
    private int itemsEarned;
    private int itemsPending;

    public ItemPieceSource(PieceSource base, List<ItemKind> kinds, RandomGenerator random) {
        this(base, kinds, random, 0);
    }

    /** 과거 줄 삭제 보상은 지급된 것으로 간주하고 지정 누적 줄 수에서 시작한다. */
    public ItemPieceSource(PieceSource base, List<ItemKind> kinds, RandomGenerator random, int initialLines) {
        if (initialLines < 0) throw new IllegalArgumentException("Initial lines must be non-negative");
        totalLines = initialLines;
        itemsEarned = initialLines / LINES_PER_ITEM;
        this.base = Objects.requireNonNull(base, "base");
        this.kinds = List.copyOf(kinds);
        this.random = Objects.requireNonNull(random, "random");
        this.upcoming = draw();
    }

    @Override
    public Piece next() {
        Piece current = upcoming;
        upcoming = draw();
        return current;
    }

    @Override
    public Piece peek() {
        return upcoming;
    }

    /** 이미 표시한 미리보기는 보존하고, 과거 보상은 지급된 것으로 간주해 집계를 다시 맞춘다. */
    public void resetLineProgress(int lines) {
        if (lines < 0) throw new IllegalArgumentException("Lines must be non-negative");
        totalLines = lines;
        itemsEarned = lines / LINES_PER_ITEM;
        itemsPending = 0;
    }

    public String debugState() {
        return "totalLines=" + totalLines + " itemsEarned=" + itemsEarned + " itemsPending=" + itemsPending;
    }

    /**
     * 엔진은 이 알림 직후 미리보기 블록을 꺼내 스폰하고 새 미리보기를 뽑는다. 따라서 여기서 기준을
     * 넘으면 곧바로 새로 나타나는 미리보기 블록에 아이템이 실린다.
     */
    @Override
    public void onLinesCleared(int lines) {
        if (lines < 0) {
            throw new IllegalArgumentException("Cleared lines must be non-negative");
        }
        totalLines += lines;
        int earned = totalLines / LINES_PER_ITEM;
        itemsPending += earned - itemsEarned;
        itemsEarned = earned;
    }

    private Piece draw() {
        Piece next = base.next();
        if (itemsPending == 0 || kinds.isEmpty()) {
            return next;
        }
        itemsPending--;
        ItemKind kind = kinds.get(random.nextInt(kinds.size()));
        return Objects.requireNonNull(kind.toItemPiece(next, random), "item piece");
    }
}
