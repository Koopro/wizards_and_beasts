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

    /**
     * Anything a splinch will not tear loose, whatever else it takes.
     *
     * <p>Ships empty on purpose: this mod has nothing that qualifies, and the tag exists for the packs and
     * addons that do. A quest item, a key, a one-of-a-kind reward — anything whose loss is unrecoverable
     * rather than merely expensive — goes here, and the residue steps over it.
     *
     * <p>Worn armour and a held wand are <b>not</b> covered by this and never needed to be: the residue only
     * ever reaches into the pack itself, so the tag is for things sitting in the pack that still must not be
     * dropped.
     */
    public static final TagKey<Item> SPLINCH_IMMUNE = ItemTags.create(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "splinch_immune"));

    private SplinchTags() {}
}
