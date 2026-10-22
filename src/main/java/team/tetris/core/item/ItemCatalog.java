package team.tetris.core.item;

import java.util.List;
import java.util.Objects;

/**
 * 아이템 모드에 등장하는 아이템 목록. 새 아이템은 {@link Item}(과 필요하면 {@link ItemKind})을
 * 구현한 뒤 {@link #entries()}에 이름과 생성 방식을 추가하면 게임과 디버그 목록에 나온다.
 * 아이템이 나올 차례에는 이 목록에서 하나를
 * 균등 확률로 고른다.
 */
public final class ItemCatalog {

    private ItemCatalog() {
    }

    public record Entry(String label, ItemKind kind) {
        public Entry {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry("줄 삭제", new AttachedItem(new LineClearItem())),
                new Entry("무게추", new WeightBlock()),
                new Entry("연쇄 삭제", new AttachedItem(new ChainClearItem())),
                new Entry("폭탄", new SoloBlock(new BombItem())),
                new Entry("점수 2배", new AttachedItem(new DoubleScoreItem())));
    }

    public static List<ItemKind> all() {
        return entries().stream().map(Entry::kind).toList();
    }
}
