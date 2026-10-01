package at.koopro.wizardsandbeasts.admin.spell;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.entity.dummy.DuellingDummyEntity;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.cast.SpellExecutor;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

/**
 * Casts a spell for an administrator at a target the <em>server</em> chooses.
 *
 * <p>The client names a spell and a {@link TargetMode}, plus a player id for {@link TargetMode#PLAYER}; it never
 * names an entity or a position. The server resolves the target itself — the entity the admin is actually
 * looking at, the nearest duelling dummy, an online player in range — turns the admin to face it, and casts
 * through the real pipeline ({@code SpellCastService.contextFor} → {@code SpellExecutor.executeGeneric}): wand,
 * allegiance, proficiency and modifiers apply, a wand can misfire.
 *
 * <p>Like {@code /wandb magic spell cast}, the gates in front of a cast are skipped (knowing the spell, its
 * requirement, its cooldown, and whether it is enabled — an administrator may test a spell before switching it
 * on) and no cooldown is stamped. Held beams are refused: they are fed by a held wand every tick, which a single
 * request cannot do.
 */
@NullMarked
public final class SpellTestService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double REACH = 32.0;
    private static final double DUMMY_RADIUS = 24.0;
    private static final String KEY = "admin.wizards_and_beasts.spell_test.";

    private SpellTestService() {}

    public enum TargetMode {
        /** The admin themself — only for self-cast spells. */
        SELF,
        /** The living entity the admin is looking at, by the server's own ray. */
        LOOKED_AT,
        /** The nearest duelling dummy within {@value #DUMMY_RADIUS} blocks. */
        NEAREST_DUMMY,
        /** An online player, in the same dimension and within reach. Needs the players capability. */
        PLAYER;

        public static @Nullable TargetMode byName(String name) {
            for (TargetMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return null;
        }
    }

    /**
     * @param success   whether a cast happened
     * @param messageKey lang key describing the outcome
     * @param detail     an argument for the message (the target's name, the spell id)
     */
    public record Outcome(boolean success, String messageKey, String detail) {
        static Outcome refused(String reason, String detail) {
            return new Outcome(false, KEY + reason, detail);
        }
    }

    public static Outcome test(ServerPlayer admin, String spellId, TargetMode mode, @Nullable UUID playerId) {
        AdminContext actor = AdminContext.of(admin);
        if (!actor.canModify(AdminCapability.CONTENT)
                || (mode == TargetMode.PLAYER && !actor.canModify(AdminCapability.PLAYERS))) {
            LOGGER.warn("[Admin] Refused spell test from unauthorised {} ({})", admin.getName().getString(), admin.getUUID());
            return Outcome.refused("unauthorized", spellId);
        }
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            return Outcome.refused("unknown_spell", spellId);
        }
        SpellProperties props = spell.getProperties();
        if (!spell.isImplemented() || props == null) {
            return Outcome.refused("not_implemented", spell.getId());
        }
        CastType castType = props.getCastType();
        if (castType == CastType.BEAM_CHANNEL || castType == CastType.BEAM_LETHAL) {
            return Outcome.refused("beam", spell.getId());
        }
        boolean selfCast = castType == CastType.SELF;
        if (selfCast != (mode == TargetMode.SELF)) {
            return Outcome.refused(selfCast ? "self_only" : "needs_target", spell.getId());
        }

        ServerLevel level = admin.level();
        Entity target = selfCast ? admin : resolveTarget(admin, level, mode, playerId);
        if (target == null) {
            return Outcome.refused("no_target", mode.name().toLowerCase(Locale.ROOT));
        }
        if (target != admin) {
            // Aim the real cast at the target: the pipeline casts along the caster's look, as a player's does.
            Vec3 aim = target.getBoundingBox().getCenter();
            admin.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
        }
        try {
            SpellExecutor.executeGeneric(SpellCastService.contextFor(admin, spell, level), level);
        } catch (RuntimeException failed) {
            LOGGER.error("[Admin] Spell test of {} by {} failed", spell.getId(), admin.getName().getString(), failed);
            return Outcome.refused("failed", spell.getId());
        }
        LOGGER.info("[Admin] {} test-cast {} at {}", admin.getName().getString(), spell.getId(), target.getName().getString());
        return new Outcome(true, KEY + "cast", target.getName().getString());
    }

    private static @Nullable Entity resolveTarget(ServerPlayer admin, ServerLevel level, TargetMode mode,
                                                  @Nullable UUID playerId) {
        return switch (mode) {
            case SELF -> admin;
            case LOOKED_AT -> lookedAt(admin);
            case NEAREST_DUMMY -> level.getEntitiesOfClass(DuellingDummyEntity.class,
                            new AABB(admin.blockPosition()).inflate(DUMMY_RADIUS), Entity::isAlive).stream()
                    .min(Comparator.comparingDouble(admin::distanceToSqr))
                    .orElse(null);
            case PLAYER -> {
                if (playerId == null) {
                    yield null;
                }
                ServerPlayer other = level.getServer().getPlayerList().getPlayer(playerId);
                yield other != null && other != admin && other.level() == level
                        && other.distanceToSqr(admin) <= REACH * REACH ? other : null;
            }
        };
    }

    /** The living entity on the admin's line of sight within reach, by the server's ray — not the client's word. */
    private static @Nullable LivingEntity lookedAt(ServerPlayer admin) {
        Vec3 eye = admin.getEyePosition();
        Vec3 end = eye.add(admin.getLookAngle().scale(REACH));
        AABB sweep = admin.getBoundingBox().expandTowards(admin.getLookAngle().scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(admin, eye, end, sweep,
                entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator(), REACH * REACH);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity living) || !admin.hasLineOfSight(living)) {
            return null;
        }
        return living;
    }
}
