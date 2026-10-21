package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * One 16px glyph per sheet page, drawn from art the mod already ships: the sheet's own inventory-tab seal for
 * Identity, the Power glyph for Attributes, and an item for everything else — the book of spells, a wand, a quill,
 * the Monster Book, the Sorting Hat, a Galleon, the Ministry handbook, a broom.
 *
 * <p>Items rather than new icon files, so there is nothing for a resource pack to forget: a missing mod item falls
 * back to a vanilla one with the same reading (book, stick, feather…), and never to the checkerboard.
 */
@NullMarked
public final class SheetIcons {

    public static final int SIZE = 16;

    private static final Map<SheetTab, ItemStack> STACKS = new EnumMap<>(SheetTab.class);

    private SheetIcons() {}

    public static void draw(GuiGraphics g, SheetTab tab, int x, int y) {
        switch (tab) {
            case IDENTITY -> CharacterSheetTextures.drawTabIcon(g, x, y, SIZE);
            case ATTRIBUTES -> {
                if (StatIcons.isPresent()) {
                    StatIcons.drawStat(g, PlayerStat.POWER, x, y);
                } else {
                    g.renderItem(stack(tab), x, y);
                }
            }
            default -> g.renderItem(stack(tab), x, y);
        }
    }

    private static ItemStack stack(SheetTab tab) {
        return STACKS.computeIfAbsent(tab, t -> switch (t) {
            case IDENTITY, ATTRIBUTES -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case MAGIC -> item("standard_book_of_spells", Items.ENCHANTED_BOOK);
            case WAND -> item("wand", Items.STICK);
            case SKILLS -> item("quill", Items.FEATHER);
            case CREATURES -> item("monster_book_of_monsters", Items.BOOK);
            case EDUCATION -> item("sorting_hat", Items.WRITABLE_BOOK);
            case PROFESSION -> item("galleon", Items.GOLD_NUGGET);
            case REPUTATION -> item("ministry_handbook", Items.NAME_TAG);
            case TRAVEL -> item("nimbus_2000", Items.FEATHER);
        });
    }

    private static ItemStack item(String path, Item fallback) {
        Identifier id = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(fallback);
        return new ItemStack(item);
    }
}
