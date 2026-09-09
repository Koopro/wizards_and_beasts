package at.koopro.wizardsandbeasts.creature.bond;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * How a bonded species raises young.
 *
 * <p>Opt-in on top of the bond rather than a separate system, because the interesting requirement is
 * the bond: a creature you have never fed will not breed for you, so this is the second thing the
 * relationship is <em>for</em> after the gift. It is also why this is not vanilla's
 * {@code Animal}/{@code AgeableMob} pair — those key off nothing but food, and every creature here
 * extends {@code PathfinderMob} rather than {@code Animal} anyway, so inheriting them would mean
 * re-registering every entity type.
 *
 * <p>Juveniles are the <b>same</b> {@code EntityType} as their parents, shrunk through
 * {@link net.minecraft.world.entity.ai.attributes.Attributes#SCALE} and grown back by a timer. That
 * matters: an {@code EntityType} is frozen registry data created at mod-init, so a real baby variant
 * would be a registration, a renderer, a spawn egg and a save-compat story per species. A scaled
 * adult is none of those, and {@code SCALE} drives the hitbox as well as the model, so a calf is
 * genuinely small rather than looking it.
 *
 * @param item              what puts a pair in the mood; usually also one of the profile's feeds
 * @param minBond           bond both parents need before they will breed at all
 * @param cooldownSeconds   per-parent wait afterwards — the only thing bounding the population
 * @param partnerRange      how close the two have to be
 * @param loveSeconds       how long one parent waits for the other after being fed
 * @param growSeconds       how long a juvenile takes to reach full size
 * @param juvenileScale     {@code SCALE} while young, as a fraction of the adult
 * @param inheritedBondPercent how much of the average parental bond the juvenile starts with, so a
 *                          calf born to a trusted herd is not a stranger
 */
@NullMarked
public record BondBreeding(Item item, int minBond, int cooldownSeconds, double partnerRange,
                           int loveSeconds, int growSeconds, float juvenileScale,
                           int inheritedBondPercent) {

    public static final Codec<BondBreeding> CODEC = RecordCodecBuilder.<BondBreeding>create(
            instance -> instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(BondBreeding::item),
                    Codec.INT.optionalFieldOf("minBond", 50).forGetter(BondBreeding::minBond),
                    Codec.INT.optionalFieldOf("cooldownSeconds", 900).forGetter(BondBreeding::cooldownSeconds),
                    Codec.DOUBLE.optionalFieldOf("partnerRange", 8.0).forGetter(BondBreeding::partnerRange),
                    Codec.INT.optionalFieldOf("loveSeconds", 30).forGetter(BondBreeding::loveSeconds),
                    Codec.INT.optionalFieldOf("growSeconds", 1200).forGetter(BondBreeding::growSeconds),
                    Codec.FLOAT.optionalFieldOf("juvenileScale", 0.5f).forGetter(BondBreeding::juvenileScale),
                    Codec.INT.optionalFieldOf("inheritedBondPercent", 50)
                            .forGetter(BondBreeding::inheritedBondPercent)
            ).apply(instance, BondBreeding::new))
            .validate(BondBreeding::validate);

    public BondBreeding {
        minBond = Math.max(1, minBond);
        partnerRange = Math.max(1.0, partnerRange);
        loveSeconds = Math.max(1, loveSeconds);
        growSeconds = Math.max(1, growSeconds);
        juvenileScale = Math.min(1.0f, Math.max(0.05f, juvenileScale));
        inheritedBondPercent = Math.min(100, Math.max(0, inheritedBondPercent));
    }

    /**
     * Refused at load. A zero cooldown makes a bonded pair a mob spawner: the pair re-enters the mood
     * the moment it is fed again, and the herd grows until the chunk gives out.
     */
    private static DataResult<BondBreeding> validate(BondBreeding breeding) {
        if (breeding.cooldownSeconds <= 0) {
            return DataResult.error(() -> "cooldownSeconds " + breeding.cooldownSeconds
                    + " bounds the population by nothing; a fed pair would breed every tick");
        }
        return DataResult.success(breeding);
    }

    public boolean matches(ItemStack stack) {
        return stack.is(item);
    }
}
