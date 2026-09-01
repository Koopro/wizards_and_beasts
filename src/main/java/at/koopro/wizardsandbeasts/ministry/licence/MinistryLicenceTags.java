package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NullMarked;

/**
 * The two tags the licence system reads, both hand-authored rather than generated.
 *
 * <p>Membership in either is a judgement — which ingredients the Ministry controls, which mobs count
 * as officials — and this repo puts judgement tags in {@code src/main/resources/data} and derived
 * tags in the providers. See {@code ModItemTagsProvider}'s header for the split.
 */
@NullMarked
public final class MinistryLicenceTags {

    /**
     * Who a forged licence gets reported to.
     *
     * <p>The mod has no Auror mob yet — {@code WantedLevel.aurorsPerDispatch} describes a dispatch
     * nothing implements — so this ships holding the Gringotts goblins, who are the only officials a
     * player can currently be caught by. The day an Auror entity lands it joins the tag and every
     * alert in this class starts reaching it without a line of code changing.
     */
    public static final TagKey<EntityType<?>> MINISTRY_OFFICIALS = TagKey.create(
            Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ministry_officials"));

    /** Ingredients the Ministry controls the trade in. */
    public static final TagKey<Item> RESTRICTED_INGREDIENT = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "restricted_ingredient"));

    private MinistryLicenceTags() {}
}
