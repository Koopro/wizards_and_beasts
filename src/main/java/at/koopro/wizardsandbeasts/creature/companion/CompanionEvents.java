package at.koopro.wizardsandbeasts.creature.companion;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.network.companion.CompanionRosterS2CPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The companion layer's world hooks: ownership that survives distance, protection from other players, death
 * remembered, fights won together credited, and the owner's roster kept in step.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
@NullMarked
public final class CompanionEvents {

    /** Owner and companions within this many blocks share a kill. */
    private static final double SHARED_KILL_RANGE = 16.0;

    private CompanionEvents() {}

    /**
     * An owned creature never despawns. These creatures are {@code PathfinderMob}s, not vanilla {@code Animal}s,
     * so without this a bonded niffler left 128 blocks behind was simply deleted by the despawn rules.
     */
    @SubscribeEvent
    public static void onDespawn(MobDespawnEvent event) {
        if (event.getEntity() instanceof BondableBeast beast && beast.bondOwner() != null) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    /**
     * Protection: a player — or another player's creature — may hurt an owned companion only as
     * {@link CompanionRules#mayHarm} allows. Refused damage never lands, so it cannot be ground down either.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof BondableBeast beast) || beast.bondOwner() == null
                || event.getEntity().level().isClientSide()) {
            return;
        }
        Player attacker = responsiblePlayer(event.getSource().getEntity());
        if (attacker == null || Companions.mayHarm(beast, attacker)) {
            return;
        }
        event.setCanceled(true);
        if (event.getSource().getEntity() == attacker) {
            PlayerFeedback.actionBar(attacker, Component.translatable("companion.wizards_and_beasts.protected",
                    event.getEntity().getDisplayName()));
        }
    }

    /** The player behind a blow: the player, or the owner of the creature that struck. */
    static @Nullable Player responsiblePlayer(@Nullable Entity source) {
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof BondableBeast other && other.bondOwner() != null) {
            return other.resolveBondOwner();
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (dead instanceof BondableBeast beast && beast.bondOwner() != null) {
            Companions.onDied(beast);
        }
        if (!(dead instanceof Enemy)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        // A threat beaten together: the companion that landed the blow, or the owner with their companions
        // fighting alongside.
        if (killer instanceof BondableBeast beast && beast.bondOwner() != null
                && beast.resolveBondOwner() instanceof ServerPlayer owner) {
            creditFight(beast, owner);
        } else if (killer instanceof ServerPlayer owner) {
            for (PathfinderMob mob : owner.level().getEntitiesOfClass(PathfinderMob.class,
                    owner.getBoundingBox().inflate(SHARED_KILL_RANGE),
                    m -> m instanceof BondableBeast b && owner.getUUID().equals(b.bondOwner()) && m.getTarget() == dead)) {
                creditFight((BondableBeast) mob, owner);
            }
        }
    }

    private static void creditFight(BondableBeast beast, ServerPlayer owner) {
        if (Companions.profile(beast) == null) {
            return;
        }
        Companions.interact(beast, Interaction.COMBAT, owner);
        Companions.trained(beast, owner);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Companions.flushRosters(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompanionRosterS2CPayload.send(player);
        }
    }
}
