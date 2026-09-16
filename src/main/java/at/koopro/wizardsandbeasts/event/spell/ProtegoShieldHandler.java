package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.spell.cast.SpellProtegoRules;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoDarkThreats;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Where a raised Shield Charm meets a blow.
 *
 * <p>Two changes from the ward this replaces, both deliberate:
 *
 * <p><b>It absorbs instead of cancelling.</b> The shield has a pool of integrity; a hit spends from
 * it and anything the pool cannot cover still lands. A nearly-spent ward is nearly no protection,
 * which is what makes the pool worth watching.
 *
 * <p><b>It only answers attacks.</b> The old ward cancelled <em>every</em> damage event while it was
 * up — falling, drowning, starving, standing in fire, poison. A shield charm is not a life-support
 * bubble, and blocking all of that made Protego the best survival tool in the game by a wide margin.
 * An attack here means damage that came from somewhere: an attacker, a projectile, a blast.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ProtegoShieldHandler {

    /**
     * An entity tag the old implementation used to mark an active ward.
     *
     * <p>Entity tags are saved with the player while the expiry map that cleared it was not, so a
     * crash or restart with a shield up left the tag behind for good — a permanently invulnerable
     * player. Nothing writes it any more; login sweeps whatever old saves still carry.
     */
    private static final String LEGACY_ACTIVE_TAG = "neo_protego_active";

    private ProtegoShieldHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !ProtegoWardManager.anyWardActive()) {
            return;
        }
        DamageSource source = event.getSource();
        // Canon: no shield stops the Killing Curse.
        if (SpellProtegoRules.bypassesProtego(event) || !isWardable(source)) {
            return;
        }
        DamageContainer container = event.getContainer();
        float incoming = container.getNewDamage();
        if (incoming <= 0.0f) {
            return;
        }

        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Vec3 from = source.getSourcePosition();
        ProtegoShieldEntity shield = ProtegoWardManager.findProtector(victim, attacker, from);
        if (shield == null) {
            return;
        }

        // A dementor's chill costs Horribilis a third of what it costs any lesser ward — the same
        // split Dark bolts get, applied to the half of Dark magic a player actually meets.
        float absorbed = shield.absorbDamage(level, incoming, from, ProtegoDarkThreats.isDarkDamage(source));
        if (absorbed >= incoming) {
            event.setCanceled(true);
        } else if (absorbed > 0.0f) {
            // The ward emptied mid-blow: it takes what it can and the rest gets through.
            container.setNewDamage(incoming - absorbed);
        }
    }

    /**
     * Whether this damage is the sort a shield can stand in front of.
     *
     * <p>It must have come from somewhere — an attacker, the thing it threw, or a blast position.
     * Damage with no origin is the environment or the victim's own state (fall, drown, starve,
     * burn, poison, wither), and a barrier in front of them is not an answer to any of it.
     */
    private static boolean isWardable(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // the void and /kill answer to nothing
        }
        return source.getEntity() != null || source.getSourcePosition() != null;
    }

    /** Heals saves that still carry the old always-on ward tag. Costs one set lookup per login. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.getTags().contains(LEGACY_ACTIVE_TAG)) {
            player.removeTag(LEGACY_ACTIVE_TAG);
        }
    }
}
