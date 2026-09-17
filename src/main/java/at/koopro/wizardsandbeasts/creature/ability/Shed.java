package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

/**
 * The creature sheds what wizards want from it — a unicorn's hair where it grazed, a demiguise's where it rested —
 * on a slow, steady clock, so the material is found by going where the creature lives rather than by killing it.
 * Nothing new is left while an earlier sign still lies nearby, which keeps one creature from carpeting a clearing.
 * Picking a sign up is a study act (see {@link CreatureSign}).
 *
 * <p>Only a calm creature sheds: one that has been hurt recently, or is hunting, does not.
 */
public record Shed(Identifier item, int intervalTicks, int count) implements CreatureAbility {

    private static final String TIMER = "shed";
    /** Set on the first tick so a creature that has just spawned does not shed at once. Never runs out in practice. */
    private static final String ARMED = "shed_armed";
    private static final int CALM_TICKS = 600;

    public static final MapCodec<Shed> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(Shed::item),
            Codec.intRange(20, Integer.MAX_VALUE).optionalFieldOf("interval_ticks", 12000).forGetter(Shed::intervalTicks),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Shed::count)
    ).apply(instance, Shed::new));

    @Override
    public CreatureAbility.@NonNull Type type() {
        return CreatureAbility.Type.SHED;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity.getCooldown(ARMED) <= 0) {
            entity.setCooldown(ARMED, Integer.MAX_VALUE);
            entity.setCooldown(TIMER, intervalTicks);
            return;
        }
        if (entity.getCooldown(TIMER) > 0 || entity.tickCount % 20 != 0) {
            return;
        }
        boolean calm = entity.getTarget() == null && (entity.getLastHurtByMob() == null
                || entity.tickCount - entity.getLastHurtByMobTimestamp() > CALM_TICKS);
        Item resolved = BuiltInRegistries.ITEM.getValue(item);
        if (!calm || resolved == null) {
            return;
        }
        if (WildlifeRules.shedDue(entity.getCooldown(TIMER), CreatureSign.lyingNearby(entity, resolved))) {
            CreatureSign.leave(entity, resolved, count);
            entity.setCooldown(TIMER, intervalTicks);
        }
    }
}
