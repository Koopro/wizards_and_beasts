package at.koopro.wizardsandbeasts.currency.dragot;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a given vendor will take French money, and on what terms.
 *
 * <p>This is the half of the Dragot that makes it a currency rather than a coin: a Galleon is
 * accepted by everyone in Britain and always will be, so its acceptance rules are not worth
 * modelling. A Dragot's are the entire point of it.
 *
 * <p><b>Tag-driven, and refusal is the default.</b> An untagged shopkeeper is a British shopkeeper
 * who has never seen a Dragot, which is both the lore-correct default and the safe one: a pack that
 * adds a Continental trader opts them in, and no existing villager silently changes behaviour. The
 * mod ships no French shop NPC yet — Diagon Alley in this mod is a block palette players build with,
 * not a generated street with residents — so the tags ship empty of anything but the goblins, who
 * are the one vendor that certainly does change money.
 */
@NullMarked
public enum DragotAcceptance {

    /** Takes Dragots at the standing rate, like any other money. */
    ACCEPTED("accepted"),
    /**
     * Takes <em>only</em> Dragots. A wizard with nothing but British gold gets nowhere, which is what
     * makes carrying a foreign purse worth the trouble.
     */
    ONLY("only"),
    /** Will take them, grudgingly, at {@link DragotRates#PENALTY_MARKUP} over the odds. */
    PENALTY("penalty"),
    /** Foreign coin. Not here. The default for anybody not named in a tag. */
    REFUSED("refused");

    private static final String TAG_PREFIX = "dragot/";

    /** @see #ACCEPTED */
    public static final TagKey<EntityType<?>> ACCEPTS = tag("accepts");
    /** @see #ONLY */
    public static final TagKey<EntityType<?>> ONLY_TAG = tag("only");
    /** @see #PENALTY */
    public static final TagKey<EntityType<?>> PENALTY_TAG = tag("penalty");

    private final String serializedName;

    DragotAcceptance(String serializedName) {
        this.serializedName = serializedName;
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, TAG_PREFIX + path));
    }

    /**
     * How this vendor feels about Dragots.
     *
     * <p>Checked most-restrictive first, so a vendor wrongly listed in two tags behaves like the
     * stricter of them rather than depending on enum declaration order.
     */
    public static DragotAcceptance policyFor(Entity vendor) {
        EntityType<?> type = vendor.getType();
        if (type.is(ONLY_TAG)) {
            return ONLY;
        }
        if (type.is(PENALTY_TAG)) {
            return PENALTY;
        }
        if (type.is(ACCEPTS)) {
            return ACCEPTED;
        }
        return REFUSED;
    }

    /** The sum actually charged, once this vendor's attitude is applied. */
    public long adjust(long knuts) {
        return this == PENALTY ? DragotRates.withPenalty(knuts) : knuts;
    }

    public Component displayName() {
        return Component.translatable("currency.wizards_and_beasts.dragot.policy." + serializedName);
    }

    /** The line a vendor says when they will not deal on these terms, or {@code null} when they will. */
    public Component refusalFor(boolean holdingDragots) {
        if (this == REFUSED && holdingDragots) {
            return Component.translatable("currency.wizards_and_beasts.dragot.refused");
        }
        if (this == ONLY && !holdingDragots) {
            return Component.translatable("currency.wizards_and_beasts.dragot.only");
        }
        return Component.empty();
    }

    /** Whether {@link #refusalFor} would produce an actual refusal. */
    public boolean refuses(boolean holdingDragots) {
        return (this == REFUSED && holdingDragots) || (this == ONLY && !holdingDragots);
    }
}
