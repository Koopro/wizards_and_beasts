package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The bench's tools as a row of small wells: a tool on the bench is drawn in its well, a missing one leaves the well
 * empty. Replaces the tier bar both bench screens drew until the 2026-11-26 remake: the tools are what the bench now
 * asks for. Hovering a well names the tool and whether it is there.
 */
@NullMarked
final class BenchToolsRow {

    static final int CELL = 18;

    private BenchToolsRow() {}

    /** Width of the wells, for right-aligning the row. */
    static int width() {
        return BenchTool.values().length * CELL;
    }

    static void render(GuiGraphics g, Font font, UiSurface s, int left, int right, int y, int mask) {
        UiText.draw(g, font, s, Component.translatable("wandcraft.bench.tools_label"), left, y + 5, s.ink2());
        wells(g, right, y, mask);
    }

    /** The wells alone, right-aligned at {@code right}. */
    static void wells(GuiGraphics g, int right, int y, int mask) {
        int x = right - width();
        for (BenchTool tool : BenchTool.values()) {
            Ui.slot(g, x + 1, y + 1);
            if ((mask & tool.bit()) != 0) {
                g.renderItem(stack(tool), x + 1, y + 1);
            }
            x += CELL;
        }
    }

    static void tooltip(GuiGraphics g, Font font, int right, int y, int mask, int mouseX, int mouseY) {
        int x = right - width();
        for (BenchTool tool : BenchTool.values()) {
            if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                boolean here = (mask & tool.bit()) != 0;
                g.setTooltipForNextFrame(font, List.of(
                        stack(tool).getHoverName(),
                        Component.translatable("item.wizards_and_beasts.bench_tool.stage." + tool.getSerializedName()),
                        Component.translatable(here ? "wandcraft.bench.tool_here" : "wandcraft.bench.tool_missing")
                ), java.util.Optional.empty(), mouseX, mouseY);
                return;
            }
            x += CELL;
        }
    }

    static ItemStack stack(BenchTool tool) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, tool.itemId())));
    }
}
