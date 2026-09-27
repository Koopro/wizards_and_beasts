package at.koopro.wizardsandbeasts.entity.niffler;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * What a Niffler wants, how much, and what it may dig — all of it tags, so a pack can add its own treasure.
 *
 * <ul>
 *   <li>{@link #SHINY} ({@code niffler_shiny}) — everything it will pick up at all. An item not in it is not its
 *       business: that is how an item is kept safe from Nifflers (or made fair game for one).</li>
 *   <li>{@link #HIGH} and {@link #MEDIUM} rank what is in it: gold above all (the goblins' treasure-finder), then
 *       gems and silver coin; everything else shiny is low.</li>
 *   <li>{@link #ORE} ({@code niffler_shiny_blocks}) — the natural deposits it will dig out; {@link #SOFT}
 *       ({@code niffler_diggable}) — the loose ground it will burrow through to reach one. Nothing a player places
 *       as a build is in either.</li>
 * </ul>
 */
public final class NifflerTreasure {

    public static final TagKey<Item> SHINY = item("niffler_shiny");
    public static final TagKey<Item> HIGH = item("niffler_treasure_high");
    public static final TagKey<Item> MEDIUM = item("niffler_treasure_medium");
    public static final TagKey<Block> ORE = block("niffler_shiny_blocks");
    public static final TagKey<Block> SOFT = block("niffler_diggable");

    public static final int NOT_INTERESTED = 0;
    public static final int LOW = 1;
    public static final int MEDIUM_VALUE = 2;
    public static final int HIGH_VALUE = 3;

    private NifflerTreasure() {}

    /** 3 for gold-grade treasure, 2 for gems and silver, 1 for anything else shiny, 0 for what it ignores. */
    public static int value(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(SHINY)) {
            return NOT_INTERESTED;
        }
        if (stack.is(HIGH)) {
            return HIGH_VALUE;
        }
        return stack.is(MEDIUM) ? MEDIUM_VALUE : LOW;
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
    }
}
