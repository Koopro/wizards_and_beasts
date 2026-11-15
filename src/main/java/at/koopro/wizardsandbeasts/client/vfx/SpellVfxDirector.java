package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.network.spell.SpellCueS2CPayload.Stage;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A spell's visual sequence after the cast: what its hit, its hold, its ward, its ending look like. The server sends
 * a cue ({@code SpellCueS2CPayload}) when a stage happens; the release comes from the cast animation packet. This
 * class composes {@link VfxKit} pieces per spell.
 *
 * <h2>Category grammar (spell VFX renaissance, 2026-11-15)</h2>
 * <ul>
 *   <li><b>Offensive</b>: light thrown outward — a shockwave on the struck surface and the ground, a mark that stays,
 *       residue rising off it.</li>
 *   <li><b>Defensive</b>: runes — a turning circle at the ward's footprint as it rises, rings outward and the circle
 *       stuttering out as it breaks.</li>
 *   <li><b>Utility</b>: gentle — motes that climb, a soft disc, no shockwave, nothing left behind.</li>
 *   <li><b>Dark arts</b>: matter, not light — dark smoke, creep spreading over the ground under the victim, smoke
 *       curling off the caster's wand while the curse holds.</li>
 * </ul>
 * One example per category is built (Confringo, Protego, Episkey, Crucio) to be judged before the other spells get
 * theirs.
 */
@NullMarked
public final class SpellVfxDirector {

    /** Scorch tint: the grey mark multiplied to a burnt brown-black. */
    static final int SCORCH = 0xFF3A2E26;
    /** Ash-grey smoke off a scorch. */
    static final int ASH_SMOKE = 0xFF5A524C;

    /** The creep under each Crucio caster's victim, kept alive while the curse holds. */
    private static final Map<Integer, VfxQuads.Quad> CREEP = new HashMap<>();

    private SpellVfxDirector() {}

    /**
     * Development only: {@code WB_VFX_LEGACY} set turns this director off, so the showcase can photograph the look
     * before and after from one build.
     */
    private static final boolean LEGACY = System.getenv("WB_VFX_LEGACY") != null
            && !net.neoforged.fml.loading.FMLEnvironment.isProduction();

    /** A cue from the server. */
    public static void cue(String spellId, Stage stage, Vec3 pos, Vec3 normal, float scale, int argb, int entity) {
        if (LEGACY) {
            return;
        }
        Spell spell = Spells.byId(spellId);
        int colour = argb != 0 ? argb : spell != null ? spell.getColor() : MagicColours.MAGIC;
        if (SpellIds.matches(spellId, "confringo") && (stage == Stage.HIT_ENTITY || stage == Stage.HIT_BLOCK)) {
            blast(pos, normal, colour, stage == Stage.HIT_BLOCK);
        } else if (SpellIds.matches(spellId, "protego")) {
            if (stage == Stage.RAISE) {
                wardRaise(pos, scale, colour);
            } else if (stage == Stage.BREAK) {
                wardBreak(pos, scale, colour);
            }
        } else if (SpellIds.matches(spellId, "crucio") && stage == Stage.CHANNEL) {
            curseHold(pos, entity);
        }
    }

    /** A cast taken (client-side, from the cast animation packet): the release stage. */
    public static void release(String spellId, Entity caster) {
        if (LEGACY) {
            return;
        }
        if (SpellIds.matches(spellId, "episkey")) {
            Spell spell = Spells.byId(spellId);
            heal(caster, spell != null ? spell.getColor() : MagicColours.HEALING);
        }
    }

    // ── offensive: Confringo ───────────────────────────────────────────────────────────────────

    static void blast(Vec3 pos, Vec3 normal, int argb, boolean blockHit) {
        Vec3 n = normal.lengthSqr() < 1.0e-6 ? VfxKit.UP : normal.normalize();
        // The blast face: a disc of light thrown out where it struck, facing back along the bolt (or out of the wall).
        VfxKit.shockwave(pos, n, argb, 0.3f, 2.4f, 6, 0);
        boolean onFloor = blockHit && n.y > 0.7;
        Vec3 ground = onFloor ? pos : VfxKit.groundBelow(pos, 3);
        if (ground != null) {
            // The ground answers: a wider ring a tick later, a scorch that stays, embers and smoke rising off it.
            VfxKit.shockwave(ground, VfxKit.UP, argb, 0.6f, 3.8f, 9, 1);
            VfxKit.thinRing(ground, VfxKit.UP, argb, 0.4f, 2.6f, 8, 3);
            VfxKit.mark(VfxQuads.SCORCH, ground, VfxKit.UP, SCORCH, 1.8f, 260, 90);
            VfxKit.residue(ground, argb, ASH_SMOKE, 0.6f, 80);
        }
        if (blockHit && !onFloor) {
            // A wall keeps its own scorch where it was struck.
            VfxKit.mark(VfxQuads.SCORCH, pos, n, SCORCH, 1.4f, 260, 90);
            VfxKit.residue(pos.add(n.scale(0.1)), argb, ASH_SMOKE, 0.3f, 50);
        }
    }

    // ── defensive: Protego ─────────────────────────────────────────────────────────────────────

    static void wardRaise(Vec3 feet, float radius, int argb) {
        Vec3 ground = VfxKit.groundBelow(feet.add(0, 0.2, 0), 2);
        float size = Math.min(7.0f, Math.max(1.6f, radius * 2.0f));
        if (ground != null) {
            VfxKit.runeCircle(ground, argb, size, 40, 3, 16, 2.0f);
        }
        VfxKit.thinRing(feet.add(0, 1.0, 0), VfxKit.UP, argb, 0.4f, size * 1.1f, 8, 0);
    }

    static void wardBreak(Vec3 centre, float radius, int argb) {
        float size = Math.min(7.0f, Math.max(1.6f, radius * 2.0f));
        VfxKit.shockwave(centre, VfxKit.UP, argb, 0.6f, size * 1.6f, 9, 0);
        VfxKit.thinRing(centre, VfxKit.UP, argb, 0.4f, size * 1.2f, 8, 3);
        Vec3 ground = VfxKit.groundBelow(centre, 4);
        if (ground != null) {
            // The ward's runes stutter, turn back and go out.
            VfxQuads.Quad runes = VfxKit.runeCircle(ground, argb, size, 22, 0, 10, -6.0f);
            if (runes != null) {
                runes.flicker(true);
            }
        }
    }

    // ── utility: Episkey ───────────────────────────────────────────────────────────────────────

    static void heal(Entity caster, int argb) {
        Vec3 ground = VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2);
        if (ground != null) {
            VfxKit.glowDisc(ground, argb, 0.5f, 1.5f, 26);
            VfxKit.thinRing(ground, VfxKit.UP, argb, 0.4f, 1.9f, 10, 0);
        }
        VfxKit.spiral(caster.getId(), SpellFamily.LIGHT, argb, 26, 0.55);
        // A last few motes at the head as the spiral arrives.
        int id = caster.getId();
        VfxEmitters.start((level, rnd, age) -> {
            Entity e = level.getEntity(id);
            if (e != null) {
                VfxKit.scatter(e.position().add(0, e.getBbHeight() + 0.1, 0), SpellFamily.LIGHT, argb, 6, 0.25,
                        0.02);
            }
        }, 1, 1, 24);
    }

    // ── dark arts: Crucio ──────────────────────────────────────────────────────────────────────

    static void curseHold(Vec3 victimFeet, int casterId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 ground = VfxKit.groundBelow(victimFeet.add(0, 0.2, 0), 3);
        if (ground != null) {
            VfxQuads.Quad creep = CREEP.get(casterId);
            boolean same = creep != null && creep.pos.distanceToSqr(ground) < 2.25
                    && now - creep.born < creep.life;
            if (same) {
                // Still on the same victim: the creep spreads while the curse holds.
                creep.keepAlive(now, 16);
                creep.growTo(Math.min(3.4f, creep.size1 + 0.35f));
            } else {
                VfxQuads.Quad fresh = VfxQuads.spawn(VfxQuads.CORRUPTION, VfxQuads.Look.LIT, ground, VfxKit.UP,
                        0.8f, 1.6f, MagicColours.CORRUPTION, 16, 4, 12, 0.4f, VfxQuads.Growth.LAND, 0);
                if (fresh != null) {
                    CREEP.put(casterId, fresh);
                }
            }
            VfxKit.darkRise(ground, MagicColours.DARK_MAGIC, MagicColours.CORRUPTION, 0.7f, 10);
        }
        VfxKit.wandSmoke(casterId, MagicColours.DARK_MAGIC, 10, 3);
        CREEP.values().removeIf(q -> now - q.born >= q.life);
    }

    /** Test seam. */
    static VfxQuads.@Nullable Quad creepOf(int casterId) {
        return CREEP.get(casterId);
    }
}
