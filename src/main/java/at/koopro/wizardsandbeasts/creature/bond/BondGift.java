package at.koopro.wizardsandbeasts.creature.bond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NullMarked;

/**
 * What a bonded creature hands over while it is alive.
 *
 * <p>This is the half of the loop the mod did not have. Every creature material was reachable in
 * exactly one way — kill the creature — which makes the relationship layer pointless: a Bowtruckle
 * you have spent an hour feeding is worth strictly less than one you have never met, because the
 * one you never met can still be killed for parts. A gift inverts that. A bonded creature produces
 * its material on a cooldown, indefinitely, so keeping it alive out-earns killing it and the bond
 * is the reward rather than a decoration on top of one.
 *
 * <p>Deliberately not a loot table: this fires from the creature's own tick with no damage source,
 * no killer and no drop context, and loot tables are the wrong tool for a thing that is given.
 *
 * @param item            what it hands over
 * @param minBond         the bond level from which it starts giving
 * @param cooldownSeconds wait between gifts — the balance knob, since the supply is unbounded
 * @param minCount        smallest stack, at least 1
 * @param maxCount        largest stack, never below {@code minCount}
 */
@NullMarked
public record BondGift(Item item, int minBond, int cooldownSeconds, int minCount, int maxCount) {

    public static final Codec<BondGift> CODEC = RecordCodecBuilder.<BondGift>create(
            instance -> instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(BondGift::item),
                    Codec.INT.optionalFieldOf("minBond", 50).forGetter(BondGift::minBond),
                    Codec.INT.optionalFieldOf("cooldownSeconds", 600).forGetter(BondGift::cooldownSeconds),
                    Codec.INT.optionalFieldOf("minCount", 1).forGetter(BondGift::minCount),
                    Codec.INT.optionalFieldOf("maxCount", 1).forGetter(BondGift::maxCount)
            ).apply(instance, BondGift::new))
            .validate(BondGift::validate);

    public BondGift {
        minCount = Math.max(1, minCount);
        maxCount = Math.max(minCount, maxCount);
        minBond = Math.max(1, minBond);
    }

    /**
     * Refused at load. A zero cooldown turns an unbounded supply into an item printer at the rate
     * the server ticks, which is not a balance mistake so much as a duplication bug with a nicer
     * name.
     */
    private static DataResult<BondGift> validate(BondGift gift) {
        if (gift.cooldownSeconds <= 0) {
            return DataResult.error(() -> "cooldownSeconds " + gift.cooldownSeconds + " on an unbounded "
                    + "gift would produce " + BuiltInRegistries.ITEM.getKey(gift.item) + " every tick");
        }
        return DataResult.success(gift);
    }

    public ItemStack roll(RandomSource random) {
        int count = minCount == maxCount ? minCount : minCount + random.nextInt(maxCount - minCount + 1);
        return new ItemStack(item, count);
    }
}
