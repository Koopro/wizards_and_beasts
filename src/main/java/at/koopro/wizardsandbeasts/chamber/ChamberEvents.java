package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Connects a Chamber's basilisk to the heart that woke it: who fought it, its death, and a stale copy turning up.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ChamberEvents {

    /** Ticks a blow is remembered as the last one, as vanilla's melee blow is. */
    private static final int LAST_HURT_MEMORY = 100;

    private ChamberEvents() {}

    /** The heart a Chamber basilisk answers to, if it is one and its heart is loaded. */
    public static @Nullable ChamberHeartBlockEntity heartOf(ServerLevel level, Entity entity) {
        if (!entity.hasData(ModAttachments.CHAMBER_HEART.get())) {
            return null;
        }
        BlockPos pos = BlockPos.of(entity.getData(ModAttachments.CHAMBER_HEART.get()));
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ChamberHeartBlockEntity heart ? heart : null;
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && event.getSource().getEntity() instanceof ServerPlayer player) {
            ChamberHeartBlockEntity heart = heartOf(level, event.getEntity());
            if (heart != null) {
                heart.noteParticipant(player);
            }
        }
    }

    /**
     * A caster's spell is about to land on {@code target}. Bolt and cone spells deal {@code damageSources().magic()},
     * which names no attacker, so the damage event above cannot see who cast them and neither can the kill: a wizard
     * who slew the basilisk with spells was not a participant, the sword went to whoever stood nearest the dais, and
     * no fang dropped. For a Chamber's basilisk the caster is noted here, and named as the last player to hurt it, which
     * is what the kill credit and {@code killed_by_player} read.
     *
     * <p>Only for a Chamber's basilisk. Naming the caster on every spell's damage would also change mob aggro, companion
     * protection and poaching across the mod, which is a decision of its own.
     */
    public static void spellStruck(LivingEntity target, @Nullable Entity caster) {
        if (caster instanceof ServerPlayer player && target.level() instanceof ServerLevel level) {
            ChamberHeartBlockEntity heart = heartOf(level, target);
            if (heart != null) {
                heart.noteParticipant(player);
                target.setLastHurtByPlayer(player, LAST_HURT_MEMORY);
            }
        }
    }

    /**
     * A Chamber basilisk that is not the one its heart is waiting on (a copy left over from before the heart put its
     * basilisk back, say) is removed as it loads, so a Chamber never holds two.
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.loadedFromDisk()) {
            ChamberHeartBlockEntity heart = heartOf(level, event.getEntity());
            if (heart != null && !event.getEntity().getUUID().equals(heart.basilisk())) {
                event.setCanceled(true);
            }
        }
    }

    /** From {@code BasiliskEntity.die}: a Chamber basilisk's death clears its Chamber; any other's clears nothing. */
    public static void basiliskDied(ServerLevel level, LivingEntity basilisk, DamageSource cause) {
        ChamberHeartBlockEntity heart = heartOf(level, basilisk);
        if (heart != null && basilisk.getUUID().equals(heart.basilisk())) {
            heart.basiliskDied(level, basilisk, cause);
        }
    }
}
