package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.creature.schedule.AiCadence;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.NonNull;

/**
 * A creature that forgets its temper and follows a player waving the right thing at it: the Zouwu and
 * Newt's feathered toy on a stick (<i>The Crimes of Grindelwald</i>). Within {@code range}, a player with
 * {@code item} in either hand is not attacked, and the creature trots after them, stopping a couple of
 * blocks short.
 */
public record LuredByItem(Identifier item, double range) implements CreatureAbility {

    public static final MapCodec<LuredByItem> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(LuredByItem::item),
            Codec.DOUBLE.optionalFieldOf("range", 10.0).forGetter(LuredByItem::range)
    ).apply(instance, LuredByItem::new));

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.LURED_BY_ITEM;
    }

    @Override
    public void tick(@NonNull GenericBeastEntity entity) {
        if (!(entity.level() instanceof ServerLevel) || !AiCadence.every(entity, 10)) {
            return;
        }
        Item lure = BuiltInRegistries.ITEM.getValue(item);
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : AbilitySupport.nearbyPlayers(entity, range)) {
            if (player.getMainHandItem().is(lure) || player.getOffhandItem().is(lure)) {
                double d = player.distanceToSqr(entity);
                if (d < best) {
                    best = d;
                    nearest = player;
                }
            }
        }
        if (nearest == null) {
            return;
        }
        if (entity.getTarget() == nearest) {
            entity.setTarget(null);
        }
        if (best > 6.0) {
            entity.getNavigation().moveTo(nearest, 1.1);
        } else {
            entity.getNavigation().stop();
            entity.getLookControl().setLookAt(nearest);
        }
    }

    /** Whether this player is holding this creature's lure. */
    public boolean lures(Player player) {
        Item lure = BuiltInRegistries.ITEM.getValue(item);
        return player.getMainHandItem().is(lure) || player.getOffhandItem().is(lure);
    }
}
