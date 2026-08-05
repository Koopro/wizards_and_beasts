package at.koopro.wizardsandbeasts.apparition.splinch;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;

/** Tags splinching reads. */
@NullMarked
public final class SplinchTags {

    /**
     * Anything that puts a splinched wizard back together when consumed. A tag rather than a hard-coded item
     * so a potion, a healer's draught or a datapack's own remedy can join without touching this code.
     */
    public static final TagKey<Item> CURES_SPLINCH = ItemTags.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "cures_splinch"));

    private SplinchTags() {}
}
