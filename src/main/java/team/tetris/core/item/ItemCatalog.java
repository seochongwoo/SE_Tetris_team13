package team.tetris.core.item;

import java.util.List;

/**
 * 아이템 모드에 등장하는 아이템 목록. 새 아이템은 {@link Item}(과 필요하면 {@link ItemKind})을
 * 구현한 뒤 여기에 한 줄을 추가하면 게임에 나온다. 아이템이 나올 차례에는 이 목록에서 하나를
 * 균등 확률로 고른다.
 */
public final class ItemCatalog {

    private ItemCatalog() {
    }

    public static List<ItemKind> all() {
        return List.of(
                // 예: new AttachedItem(new LineClearItem()),
        );
    }
}
