package at.koopro.wizardsandbeasts.creature.bond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * One item a species will accept from a player's hand, and what offering it is worth.
 *
 * <p>The Niffler's hard-coded table — diamond 20 / gold ingot 15 / nugget 5, on 120 / 60 / 30 second
 * cooldowns — is the shape this generalises. The cooldown is per feed <em>entry</em> rather than per
 * creature so a species can offer a cheap staple and a rare treat at once without the treat being
 * reachable by spamming the staple: the good item buys more bond and locks the creature out for
 * longer, which is what makes choosing what to feed a decision at all.
 *
 * @param item            what the player must be holding
 * @param gain            bond points awarded, before the Magizoology bonus
 * @param cooldownSeconds how long this creature refuses <em>all</em> food afterwards
 */
@NullMarked
public record BondFeed(Item item, int gain, int cooldownSeconds) {

    public static final Codec<BondFeed> CODEC = RecordCodecBuilder.<BondFeed>create(
            instance -> instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(BondFeed::item),
                    Codec.INT.fieldOf("gain").forGetter(BondFeed::gain),
                    Codec.INT.optionalFieldOf("cooldownSeconds", 60).forGetter(BondFeed::cooldownSeconds)
            ).apply(instance, BondFeed::new))
            .validate(BondFeed::validate);

    public BondFeed {
        cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    /**
     * Refused at load. A feed worth nothing is silently indistinguishable from an item the creature
     * does not eat, so the mistake would surface only as "feeding it does nothing" in a bug report.
     */
    private static DataResult<BondFeed> validate(BondFeed feed) {
        if (feed.gain <= 0) {
            return DataResult.error(() -> "gain " + feed.gain + " means feeding "
                    + BuiltInRegistries.ITEM.getKey(feed.item) + " would do nothing");
        }
        return DataResult.success(feed);
    }

    public boolean matches(ItemStack stack) {
        return stack.is(item);
    }
}
