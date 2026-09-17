package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who has a shield up, and whose shield stands in front of a given blow.
 *
 * <p>One shield per caster: raising a new one replaces the old. Everything that needs to know
 * whether someone is warded goes through {@link #findProtector} rather than reading a player tag,
 * so there is a single answer and it is derived from a shield that actually exists.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ProtegoWardManager {
    private static final Map<UUID, Integer> CASTER_TO_ENTITY = new ConcurrentHashMap<>();

    private ProtegoWardManager() {}

    public static void register(@NonNull UUID casterUUID, int entityId) {
        CASTER_TO_ENTITY.put(casterUUID, entityId);
    }

    public static int getEntityId(@NonNull UUID casterUUID) {
        return CASTER_TO_ENTITY.getOrDefault(casterUUID, -1);
    }

    public static void remove(@NonNull UUID casterUUID) {
        CASTER_TO_ENTITY.remove(casterUUID);
    }

    /** True while anybody in this world has a shield up — the early out for the damage listener. */
    public static boolean anyWardActive() {
        return !CASTER_TO_ENTITY.isEmpty();
    }

    public static @Nullable ProtegoShieldEntity shieldOf(@NonNull LivingEntity caster) {
        int id = getEntityId(caster.getUUID());
        if (id < 0) {
            return null;
        }
        return caster.level().getEntity(id) instanceof ProtegoShieldEntity shield ? shield : null;
    }

    /**
     * The shield that answers for {@code victim} against this attack, or null.
     *
     * <p>The victim's own shield wins; otherwise the strongest ally dome they are standing in, so
     * two overlapping domes spend the one with more left in it.
     */
    public static @Nullable ProtegoShieldEntity findProtector(@NonNull LivingEntity victim,
                                                              @Nullable Entity attacker,
                                                              @Nullable Vec3 from) {
        if (CASTER_TO_ENTITY.isEmpty()) {
            return null;
        }
        ProtegoShieldEntity own = shieldOf(victim);
        if (own != null && own.protects(victim, attacker, from)) {
            return own;
        }
        ProtegoShieldEntity best = null;
        for (Integer entityId : CASTER_TO_ENTITY.values()) {
            if (!(victim.level().getEntity(entityId) instanceof ProtegoShieldEntity shield)) {
                continue;
            }
            if (!shield.protects(victim, attacker, from)) {
                continue;
            }
            if (best == null || shield.getIntegrity() > best.getIntegrity()) {
                best = shield;
            }
        }
        return best;
    }

    /**
     * Last line for a spell that reached a warded body: the ward eats it instead of it landing.
     *
     * <p>Needed as well as the swept interception, because a bolt can hit its target in the same
     * tick it enters the ward and before the shield gets to tick, and because a spell fired from
     * <em>inside</em> a dome is not stopped by the wall but must still not land on the people the
     * charm is protecting.
     *
     * @return true if the shield took it and the hit must not resolve
     */
    public static boolean interceptSpellHit(@NonNull ServerLevel level,
                                            @NonNull SpellProjectileEntity bolt,
                                            @NonNull LivingEntity victim) {
        if (CASTER_TO_ENTITY.isEmpty()) {
            return false;
        }
        Entity attacker = bolt.getOwner();
        if (attacker == null && bolt.getCasterUuid() != null) {
            attacker = level.getPlayerByUUID(bolt.getCasterUuid());
        }
        ProtegoShieldEntity shield = findProtector(victim, attacker, bolt.position());
        return shield != null && shield.receiveSpell(level, bolt, bolt.position(), false);
    }

    /**
     * Whether a bolt belongs to the shield's own side and should be allowed through its wall.
     *
     * <p>Strict on purpose: the caster's own bolts and their <em>team's</em> bolts pass, and nothing
     * else does. The looser "every player is a friend" rule {@link #isAlly} uses for who is
     * sheltered would, applied here, make a dome useless in any duel on a server without teams.
     */
    public static boolean isFriendlyProjectile(@NonNull ProtegoShieldEntity shield, @NonNull Projectile projectile) {
        UUID casterId = shield.getCasterUuid();
        if (casterId == null) {
            return false;
        }
        // A spell bolt knows its caster even when its owner entity has gone; an arrow only has an owner.
        if (projectile instanceof SpellProjectileEntity bolt && casterId.equals(bolt.getCasterUuid())) {
            return true;
        }
        Entity owner = projectile.getOwner();
        if (owner != null && casterId.equals(owner.getUUID())) {
            return true;
        }
        Player caster = shield.findCaster();
        return caster != null && owner != null && owner.isAlliedTo(caster);
    }

    /**
     * Who a dome shelters besides its caster.
     *
     * <p>Teams and tame pets are the reliable signal ({@code isAlliedTo} covers both). Where there
     * are no teams there is nothing to read, so every player counts as a friend — that is the
     * co-operative default the dome has always had, and the fallback disappears the moment anybody
     * puts a scoreboard team on the caster.
     */
    public static boolean isAlly(@NonNull Player caster, @NonNull LivingEntity other) {
        if (other == caster || other.isAlliedTo(caster)) {
            return true;
        }
        return caster.getTeam() == null && other instanceof Player;
    }

    /** Drops the caster's current shield quietly, because they are raising another one. */
    public static void replaceExisting(@NonNull ServerPlayer caster) {
        ProtegoShieldEntity shield = shieldOf(caster);
        if (shield != null) {
            shield.collapse(ProtegoShieldEntity.Collapse.REPLACED);
        } else {
            remove(caster.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        // Only the shields that lived in the level going away; entries for other dimensions still
        // point at live entities and clearing them would strand those wards unfindable.
        if (!(event.getLevel() instanceof Level unloading)) {
            return;
        }
        CASTER_TO_ENTITY.entrySet().removeIf(entry ->
                unloading.getEntity(entry.getValue()) instanceof ProtegoShieldEntity);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity().level().getEntity(getEntityId(id)) instanceof ProtegoShieldEntity shield) {
            shield.collapse(ProtegoShieldEntity.Collapse.CASTER_LOST);
        }
        CASTER_TO_ENTITY.remove(id);
    }
}
