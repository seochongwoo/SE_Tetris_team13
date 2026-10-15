package team.tetris.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.Random;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.tetris.application.GameCommand;
import team.tetris.application.GameStatus;
import team.tetris.application.debug.DebugSetup;
import team.tetris.application.model.Difficulty;
import team.tetris.bootstrap.AppComposition;
import team.tetris.core.Piece;
import team.tetris.core.TetrominoType;
import team.tetris.core.TemporaryBlock;
import team.tetris.core.item.ItemCatalog;
import team.tetris.ui.screen.GameScreen;

class DebugPanelTest {
    @TempDir Path directory;

    @Test void fillModeAppliesAndRestoresWithTheBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var app = new AppComposition(directory, true);
            var router = new ScreenRouter(app, app.settings().loadOrDefault(), () -> {}, ignored -> {});
            var panel = new DebugPanel(router, () -> {});
            var fillMode = (JComboBox<?>) named(panel, "fillMode");
            assertEquals(DebugSetup.FillMode.TEMPORARY, fillMode.getSelectedItem());
            ((JSpinner) named(panel, "filledRows")).setValue(5);
            button(panel, "보드·스탯 적용").doClick();
            assertSame(TemporaryBlock.INSTANCE,
                    router.debugSession().orElseThrow().snapshot().engine().board()[19][0].occupiedBy());

            fillMode.setSelectedItem(DebugSetup.FillMode.RANDOM);
            button(panel, "보드·스탯 적용").doClick();
            var session = router.debugSession().orElseThrow();
            var initialBoard = session.snapshot().engine().board();
            assertEquals(DebugSetup.FillMode.RANDOM, session.setup().fillMode());
            for (var row : initialBoard) {
                for (var cell : row) {
                    if (!cell.isEmpty()) assertInstanceOf(TetrominoType.class, cell.occupiedBy());
                }
            }
            button(panel, "보드·스탯 적용").doClick();
            assertArrayEquals(initialBoard, router.debugSession().orElseThrow().snapshot().engine().board());
            fillMode.setSelectedItem(DebugSetup.FillMode.TEMPORARY);
            button(panel, "설정한 상태로 되돌리기").doClick();
            assertEquals(DebugSetup.FillMode.RANDOM, fillMode.getSelectedItem());
            assertArrayEquals(initialBoard, router.debugSession().orElseThrow().snapshot().engine().board());
            button(panel, "보드 비우기").doClick();
            assertEquals(DebugSetup.FillMode.RANDOM, router.debugSession().orElseThrow().setup().fillMode());
            for (var row : router.debugSession().orElseThrow().snapshot().engine().board()) {
                for (var cell : row) assertTrue(cell.isEmpty());
            }
        });
    }

    @Test void everyCatalogItemCanBeSelectedAndSpawnedFromThePanel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var app = new AppComposition(directory, true);
            var router = new ScreenRouter(app, app.settings().loadOrDefault(), () -> {}, ignored -> {});
            var panel = new DebugPanel(router, () -> {});
            var items = (JComboBox<?>) named(panel, "items");
            var catalog = ItemCatalog.entries();
            assertEquals(catalog.size() + 1, items.getItemCount());
            assertEquals("없음", items.getItemAt(0).toString());
            ((JComboBox<?>) named(panel, "pieces")).setSelectedItem(TetrominoType.I);
            for (int i = 0; i < items.getItemCount(); i++) {
                button(panel, "보드 비우기").doClick();
                items.setSelectedIndex(i);
                Piece expected = Piece.of(TetrominoType.I);
                if (i > 0) {
                    var entry = catalog.get(i - 1);
                    assertEquals(entry.label(), items.getSelectedItem().toString());
                    expected = entry.kind().toItemPiece(expected, new Random(13));
                }
                button(panel, "다음 블록에 적용").doClick();
                var session = router.debugSession().orElseThrow();
                Piece preview = session.snapshot().engine().nextPiece();
                assertEquals(expected.shape(), preview.shape());
                assertEquals(expected.items().keySet(), preview.items().keySet());
                expected.items().forEach((index, item) ->
                        assertEquals(item.symbol(), preview.itemAt(index).symbol()));
                button(panel, "다음 블록에 적용").doClick();
                assertEquals(preview, session.snapshot().engine().nextPiece());
                session.handle(GameCommand.HARD_DROP);
                assertEquals(preview, session.snapshot().engine().activePiece().piece());
            }
        });
    }

    @Test void panelAppliesBoardStatsAndRestartsTheAppliedState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var app = new AppComposition(directory, true);
            var router = new ScreenRouter(app, app.settings().loadOrDefault(), () -> {}, ignored -> {});
            var panel = new DebugPanel(router, () -> {});
            assertFalse(button(panel, "설정한 상태로 되돌리기").isEnabled());
            ((JSpinner) named(panel, "filledRows")).setValue(4);
            ((JSpinner) named(panel, "holesPerRow")).setValue(2);
            ((JSpinner) named(panel, "clearedLines")).setValue(29);
            ((JComboBox<?>) named(panel, "difficulty")).setSelectedItem(Difficulty.HARD);
            button(panel, "보드·스탯 적용").doClick();
            assertInstanceOf(GameScreen.class, router.current());
            var session = router.debugSession().orElseThrow();
            assertEquals(4, session.setup().filledRows());
            assertEquals(2, session.setup().holesPerRow());
            assertEquals(29, session.snapshot().clearedLines());
            assertEquals(Difficulty.HARD, session.difficulty());
            assertTrue(session.frozen());
            var initialBoard = session.snapshot().engine().board();
            button(panel, "중력 한 단계 (낙하 / 고정)").doClick();
            assertEquals(3, session.snapshot().score());
            ((JSpinner) named(panel, "clearedLines")).setValue(9);
            ((JComboBox<?>) named(panel, "difficulty")).setSelectedItem(Difficulty.EASY);
            button(panel, "스탯만 적용").doClick();
            assertSame(session, router.debugSession().orElseThrow());
            assertEquals(9, session.snapshot().clearedLines());
            assertEquals(3, session.snapshot().score());
            assertArrayEquals(initialBoard, session.snapshot().engine().board());
            for (int i = 0; i < 50 && session.result().isEmpty(); i++) session.handle(GameCommand.HARD_DROP);
            router.update(5_000_000_000L);
            assertSame(session, router.debugSession().orElseThrow());
            assertEquals(GameStatus.GAME_OVER, session.snapshot().status());
            panel.refresh();
            assertFalse(button(panel, "중력 한 단계 (낙하 / 고정)").isEnabled());
            button(panel, "설정한 상태로 되돌리기").doClick();
            var restarted = router.debugSession().orElseThrow();
            assertNotSame(session, restarted);
            assertEquals(session.setup(), restarted.setup());
            assertArrayEquals(initialBoard, restarted.snapshot().engine().board());
            assertEquals(29, restarted.snapshot().clearedLines());
            assertEquals(Difficulty.HARD, restarted.difficulty());
            assertEquals(0, restarted.snapshot().score());
            button(panel, "보드 비우기").doClick();
            for (var row : router.debugSession().orElseThrow().snapshot().engine().board()) {
                for (var cell : row) assertTrue(cell.isEmpty());
            }
            button(panel, "메뉴로").doClick();
            assertTrue(router.debugSession().isEmpty());
        });
    }

    @Test void invalidTypedNumberDoesNotReplaceTheGameAndEditingClearsQueuedMoves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var app = new AppComposition(directory, true);
            var router = new ScreenRouter(app, app.settings().loadOrDefault(), () -> {}, ignored -> {});
            var panel = new DebugPanel(router, () -> {});
            button(panel, "보드·스탯 적용").doClick();
            var session = router.debugSession().orElseThrow();
            var field = ((JSpinner.DefaultEditor) ((JSpinner) named(panel, "filledRows")).getEditor()).getTextField();
            field.setText("invalid");
            button(panel, "보드·스탯 적용").doClick();
            assertSame(session, router.debugSession().orElseThrow());
            var initial = session.snapshot().engine().activePiece();
            router.keyPressed("LEFT");
            session.setFrozen(false);
            router.freezeDebugInput();
            router.update(1_000_000_000);
            assertTrue(session.frozen());
            assertEquals(initial, session.snapshot().engine().activePiece());
        });
    }

    private static JButton button(DebugPanel panel, String text) {
        for (var component : panel.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
        }
        throw new AssertionError("Missing button: " + text);
    }

    private static Component named(Container parent, String name) {
        for (var component : parent.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) {
                Component found = named(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
