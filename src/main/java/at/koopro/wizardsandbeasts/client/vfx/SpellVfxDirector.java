package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.network.spell.SpellCueS2CPayload.Stage;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.client.beam.WandTipTracker;
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
        Spell spell = Spells.byId(spellId);
        int colour = argb != 0 ? argb : spell != null ? spell.getColor() : MagicColours.MAGIC;
        String id = base(spellId);
        if (LEGACY) {
            if (stage == Stage.LINE) {
                plainLine(spell, pos, pos.add(normal), colour);
            }
            return;
        }
        switch (stage) {
            case HIT_ENTITY, HIT_BLOCK -> hit(id, pos, normal, colour, stage == Stage.HIT_BLOCK);
            case LINE -> line(id, spell, pos, pos.add(normal), colour);
            case CHANNEL -> channel(id, pos, colour, entity);
            case RAISE -> {
                if (id.equals("protego")) {
                    wardRaise(pos, scale, colour);
                }
            }
            case BREAK -> {
                if (id.equals("protego")) {
                    wardBreak(pos, scale, colour);
                }
            }
        }
    }

    /** A cast taken (client-side, from the cast animation packet): the release stage. */
    public static void release(String spellId, Entity caster) {
        if (LEGACY) {
            return;
        }
        Spell spell = Spells.byId(spellId);
        int colour = spell != null ? spell.getColor() : MagicColours.MAGIC;
        self(base(spellId), caster, colour);
    }

    static String base(String spellId) {
        int colon = spellId.indexOf(':');
        return colon >= 0 ? spellId.substring(colon + 1) : spellId;
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

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    // The rollout (owner, 2026-11-15: "go with the rest and make it film accurate"). Film is the reference where a
    // spell is on screen (docs/visual-renaissance/LORE_VISUAL_REFERENCE.md); where canon gives a spell no light,
    // its effect is the visual and the look stays quiet.
    // ═════════════════════════════════════════════════════════════════════════════════════════════

    static final int STEEL = 0xFFD8DCE8;
    static final int CUT_MARK = 0xFF2A2A30;
    static final int FROST = 0xFF8CCBEE;
    static final int ICE_MIST = 0xFFCFE6F5;
    static final int WET = 0xFF39434C;
    static final int SILVER = 0xFFDDEEFF;
    static final int GOLD_GLINT = 0xFFFFE08A;
    static final int OBSCURUS_BLACK = 0xFF101014;
    static final int EMBER = 0xFFFF5A1E;
    static final int[] CONFETTI = {0xFFFF5FC8, 0xFFFFE08A, 0xFF7FD8FF, 0xFF9CFF8A, 0xFFFFA030};

    /** Up a little from a body's centre to its head. */
    private static Vec3 head(Vec3 centre) {
        return centre.add(0, 0.55, 0);
    }

    private static Vec3 unit(Vec3 v) {
        return v.lengthSqr() < 1.0e-6 ? VfxKit.UP : v.normalize();
    }

    // ── a bolt arrives ─────────────────────────────────────────────────────────────────────────

    static void hit(String id, Vec3 pos, Vec3 normal, int c, boolean block) {
        Vec3 n = unit(normal);
        switch (id) {
            case "confringo" -> blast(pos, n, c, block);
            // Film (Deathly Hallows): a blasting jinx bursts bigger than Confringo; the explosion itself is vanilla's.
            case "expulso" -> {
                blast(pos, n, c, block);
                VfxKit.shockwave(pos, n, c, 0.5f, 3.2f, 7, 1);
            }
            // Film: the Stunner is a red bolt that bursts red on the body and throws it back.
            case "stupefy" -> jetHit(pos, n, c, SpellFamily.ELECTRIC, 2.0f, 12);
            // Film: the Disarming Charm is a scarlet bolt; a scarlet flash where it lands, sparks flying off.
            case "expelliarmus" -> {
                jetHit(pos, n, c, SpellFamily.LIGHT, 2.2f, 8);
                VfxKit.gust(pos, n, c, 10, 0.25, 0.08);
            }
            // Game (Philosopher's Stone): the knockback jinx is a shove; motes thrown on along the blow.
            case "flipendo" -> {
                jetHit(pos, n, c, SpellFamily.ARCANE, 1.8f, 6);
                VfxKit.gust(pos, n.reverse(), c, 12, 0.35, 0.06);
            }
            // Canon: a cutting curse, no light. A cold stroke where it bit, and a cut left on stone.
            case "diffindo" -> {
                VfxQuads.spawn(VfxQuads.SLASH, VfxQuads.Look.GLOW, pos, n, 0.8f, 2.6f, STEEL, 8, 0, 5, 0f,
                        VfxQuads.Growth.BURST, 0);
                VfxKit.scatter(pos, SpellFamily.ELECTRIC, STEEL, 6, 0.15, 0.0);
                if (block) {
                    VfxKit.mark(VfxQuads.SLASH, pos, n, CUT_MARK, 1.0f, 200, 60);
                }
            }
            // Canon: grows. Rings swelling out round the target, one after another.
            case "engorgio" -> VfxKit.pulses(pos, VfxKit.UP, c, 0.5f, 2.6f, 9, 3, 3);
            // Canon: shrinks. The same rings closing in.
            case "reducio" -> VfxKit.closing(pos, VfxKit.UP, c, 2.6f, 0.3f, 9, 3, 3);
            // Canon: boils (GoF 18). Little sickly blisters bursting on the body, motes rising off them.
            case "furnunculus" -> {
                VfxEmitters.start((level, rnd, age) -> {
                    for (int k = 0; k < 2; k++) {
                        Vec3 p = pos.add(rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.4,
                                rnd.nextGaussian() * 0.25);
                        VfxQuads.spawn(VfxQuads.RING_THIN, VfxQuads.Look.GLOW, p, n, 0.1f, 0.8f, c, 6, 0, 3, 0f,
                                VfxQuads.Growth.BURST, 0);
                        VfxKit.moteAt(level, p, new Vec3(0, 0.03, 0), c);
                    }
                    if (age % 4 == 0) {
                        VfxKit.mist(pos, c, 1, 0.2, 0.01);
                    }
                }, 16, 2);
            }
            // Canon: the Impediment Jinx. A slow amber ring and motes sinking as if through treacle.
            case "impedimenta" -> {
                VfxKit.slowRing(pos, VfxKit.UP, c, 0.4f, 2.4f, 34, 0);
                VfxKit.sink(pos.add(0, -0.6, 0), c, 0.5, 30);
            }
            // Film (Philosopher's Stone): Neville goes rigid. Bands close round the body at three heights.
            case "petrificus_totalus" -> {
                for (int k = -1; k <= 1; k++) {
                    VfxKit.closing(pos.add(0, k * 0.6, 0), VfxKit.UP, c, 1.8f, 0.7f, 10, 1, 0);
                }
                VfxKit.converge(pos, c, 0.9, 6, 2);
            }
            // Film (Chamber of Secrets): Harry's legs dance. Pink rings bounce at the feet, sparks at the ankles.
            case "tarantallegra" -> {
                Vec3 ground = VfxKit.groundBelow(pos, 3);
                Vec3 feet = ground != null ? ground : pos.add(0, -0.9, 0);
                VfxKit.pulses(feet, VfxKit.UP, c, 0.3f, 1.4f, 6, 6, 5);
                VfxKit.rise(feet, c, 0.4, 30, 0.05);
            }
            // Canon: tongue to the roof of the mouth. A small pink puff at the mouth.
            case "langlock" -> {
                Vec3 h = head(pos);
                VfxKit.mist(h, c, 3, 0.12, 0.0);
                VfxKit.closing(h, n, c, 0.8f, 0.15f, 6, 1, 0);
            }
            // Canon: the Confundus Charm. Lilac motes circling the head.
            case "confundo" -> VfxKit.orbit(head(pos).add(0, 0.2, 0), c, 0.45, 40, 3);
            // Canon (DH 15): a blindfold. Dark indigo smoke wrapping the eyes.
            case "obscuro" -> VfxEmitters.start((level, rnd, age) -> {
                double a = age * 0.6;
                Vec3 h = head(pos);
                VfxKit.mist(h.add(Math.cos(a) * 0.32, 0.05, Math.sin(a) * 0.32), c, 1, 0.03, 0.0);
            }, 30, 2);
            // Film: the Killing Curse ends in a flash of green light. No mark: it leaves nothing on the body.
            case "avada_kedavra" -> {
                VfxKit.shockwave(pos, n, c, 0.4f, 2.6f, 6, 0);
                VfxKit.thinRing(pos, n, c, 0.3f, 3.4f, 8, 2);
                VfxKit.gust(pos, n, c, 14, 0.2, 0.12);
            }
            default -> jetHit(pos, n, c, SpellFamily.ARCANE, 1.8f, 6);
        }
    }

    /** The common jet arrival: a disc of the spell's light facing back along the jet, a ring after it, sparks. */
    static void jetHit(Vec3 pos, Vec3 n, int c, SpellFamily sparks, float size, int count) {
        VfxKit.shockwave(pos, n, c, 0.3f, size, 6, 0);
        VfxKit.thinRing(pos, n, c, 0.3f, size * 1.3f, 7, 1);
        VfxKit.scatter(pos, sparks, c, count, 0.25, 0.0);
    }

    // ── a cone or targeted cast reaches out ────────────────────────────────────────────────────

    static void line(String id, @Nullable Spell spell, Vec3 from, Vec3 to, int c) {
        Vec3 dir = unit(to.subtract(from));
        switch (id) {
            // Film (Goblet of Fire, the Firebolt): no light; the thing just comes. Motes streaming back to the wand.
            case "accio" -> {
                VfxKit.pull(from, to, c, 14);
                VfxKit.closing(to, dir, c, 1.2f, 0.2f, 6, 1, 0);
            }
            // Canon: banishing, no light. A push of air: discs thrown out along the cast, motes blown away.
            case "depulso" -> {
                VfxKit.shockwave(from.add(dir.scale(1.2)), dir, c, 0.3f, 1.6f, 5, 0);
                VfxKit.shockwave(from.add(dir.scale(2.6)), dir, c, 0.4f, 2.4f, 6, 2);
                VfxKit.gust(from.add(dir), dir, c, 14, 0.45, 0.06);
            }
            // Game: a freezing charm. A line of ice, frost blooming where it ends, a breath of cold.
            case "glacius" -> {
                VfxKit.line(from, to, SpellFamily.ICE, c, 10);
                // Frost runs along the ground under the whole cast, thickening toward its end.
                frost(from.lerp(to, 0.4), 1.2f);
                frost(from.lerp(to, 0.7), 1.5f);
                frost(to, 1.9f);
                VfxKit.scatter(to, SpellFamily.ICE, FROST, 10, 0.3, 0.0);
            }
            // Film: fire from the wand. Embers driven along the jet, a scorch where it lands.
            case "incendio" -> {
                VfxKit.line(from, to, SpellFamily.FIRE, c, 12);
                VfxKit.gust(from.add(dir.scale(0.6)), dir, c, 14, 0.5, 0.05);
                Vec3 ground = VfxKit.groundBelow(to, 2);
                if (ground != null) {
                    VfxKit.mark(VfxQuads.SCORCH, ground, VfxKit.UP, SCORCH, 1.1f, 160, 60);
                    VfxKit.residue(ground, c, ASH_SMOKE, 0.4f, 30);
                }
            }
            // Canon: a flash and the victim hoisted by the ankle. A thin line, a ring at the feet, motes going up.
            case "levicorpus" -> {
                VfxKit.line(from, to, SpellFamily.ARCANE, c, 6);
                Vec3 ground = VfxKit.groundBelow(to, 3);
                Vec3 feet = ground != null ? ground : to.add(0, -0.9, 0);
                VfxKit.shockwave(feet, VfxKit.UP, c, 0.3f, 1.6f, 6, 0);
                VfxKit.rise(feet, c, 0.4, 14, 0.12);
            }
            // The counter-jinx: the same ring, motes coming down.
            case "liberacorpus" -> {
                VfxKit.line(from, to, SpellFamily.ARCANE, c, 6);
                VfxKit.thinRing(to, VfxKit.UP, c, 0.3f, 1.6f, 7, 0);
                VfxKit.sink(to.add(0, 0.6, 0), c, 0.4, 14);
            }
            // Film (Philosopher's Stone): no light, a click. A small golden glint at the lock.
            case "alohomora" -> {
                Vec3 lock = to.add(dir.scale(-0.05));
                VfxKit.thinRing(lock, dir.reverse(), GOLD_GLINT, 0.1f, 0.7f, 6, 0);
                VfxKit.scatter(lock, SpellFamily.LIGHT, GOLD_GLINT, 5, 0.1, 0.01);
            }
            // Canon: the door seals with a squelch. A dark ring closing on the face of it.
            case "colloportus" -> {
                Vec3 face = to.add(dir.scale(-0.05));
                VfxKit.closing(face, dir.reverse(), c, 0.9f, 0.15f, 8, 1, 0);
                VfxKit.mist(face, MagicColours.DARK_MAGIC, 2, 0.08, 0.0);
            }
            // Film (Chamber of Secrets, the duelling club): a pulse goes out and the jinxes drop.
            case "finite_incantatem" -> {
                VfxKit.line(from, to, SpellFamily.LIGHT, c, 5);
                VfxKit.pulses(to, VfxKit.UP, c, 0.3f, 2.6f, 9, 2, 3);
            }
            // Film (Prisoner of Azkaban): a blast. Vanilla's explosion stays; a bigger shockwave and scorch with it.
            case "bombarda" -> {
                VfxKit.line(from, to, SpellFamily.FIRE, c, 8);
                blast(to, dir.reverse(), c, true);
                VfxKit.shockwave(to, VfxKit.UP, c, 0.6f, 4.6f, 10, 1);
            }
            // Film (Fantastic Beasts): the Obscurus reaches as black roiling smoke; the ground cracks where it grips.
            case "obscurus_grasp" -> {
                VfxEmitters.start((level, rnd, age) -> {
                    Vec3 p = from.lerp(to, rnd.nextDouble());
                    VfxKit.mist(p, OBSCURUS_BLACK, 1, 0.12, 0.0);
                    VfxKit.moteAt(level, p, Vec3.ZERO, MagicColours.DARK_MAGIC);
                }, 10, 1);
                obscurusCrack(VfxKit.groundBelow(to, 3), 1.6f);
            }
            default -> plainLine(spell, from, to, c);
        }
    }

    /** The cast's line as it always was: the spell's trail particle from wand to target. */
    static void plainLine(@Nullable Spell spell, Vec3 from, Vec3 to, int c) {
        SpellFamily family = spell != null ? spell.vfx().trail().particleFamily() : SpellFamily.ARCANE;
        VfxKit.line(from, to, family, c, 10);
    }

    // ── a held spell, on a slow beat ───────────────────────────────────────────────────────────

    static void channel(String id, Vec3 pos, int c, int entity) {
        switch (id) {
            case "crucio" -> curseHold(pos, entity);
            // Canon/film (Half-Blood Prince, the cave): a jet of water. It splashes and wets where it lands.
            case "aguamenti" -> {
                Vec3 ground = VfxKit.groundBelow(pos.add(0, 0.6, 0), 2);
                Vec3 spot = ground != null ? ground : pos;
                VfxKit.thinRing(spot, VfxKit.UP, c, 0.3f, 1.5f, 8, 0);
                VfxKit.mark(VfxQuads.GLOW_DISC, spot, VfxKit.UP, WET, 1.3f, 120, 50);
                VfxKit.gust(spot.add(0, 0.1, 0), VfxKit.UP, c, 6, 0.18, 0.08);
            }
            // Canon: swish and flick, no light (PS 10). The lifted thing sheds a few motes; a faint glow below it.
            case "wingardium_leviosa" -> {
                VfxKit.rise(pos, c, 0.45, 10, 0.03);
                Vec3 ground = VfxKit.groundBelow(pos, 6);
                if (ground != null) {
                    VfxKit.glowDisc(ground, c, 0.6f, 1.0f, 14);
                }
            }
            // Canon: light at the wand tip (CoS 15). Kept lit while the charm holds; the server says so each second.
            case "lumos" -> VfxKit.tipGlow(entity, 0xFFFFFCEB, 22);
            default -> { }
        }
    }

    // ── a charm cast on oneself, at the release ────────────────────────────────────────────────

    static void self(String id, Entity caster, int c) {
        int cid = caster.getId();
        Vec3 look = caster.getLookAngle();
        switch (id) {
            case "episkey" -> heal(caster, c);
            // Film: the tip flares white and stays lit.
            case "lumos" -> VfxKit.atTip(cid, 2, (who, tip) -> {
                VfxKit.scatter(tip, SpellFamily.LIGHT, 0xFFFFFCEB, 10, 0.08, 0.0);
                VfxKit.thinRing(tip, look, 0xFFFFFCEB, 0.1f, 0.9f, 5, 0);
                VfxKit.tipGlow(cid, 0xFFFFFCEB, 20);
            });
            // The light drawn back into the wand and out.
            case "nox" -> VfxKit.atTip(cid, 2, (who, tip) -> {
                VfxKit.converge(tip, 0xFFFFF4C8, 0.7, 8, 2);
                VfxKit.mist(tip, MagicColours.DARK_MAGIC, 1, 0.02, 0.0);
            });
            // Film (Half-Blood Prince, the glasses): the pieces fly back together. Motes drawn into a point.
            case "reparo" -> VfxKit.atTip(cid, 2, (who, tip) -> {
                Vec3 point = tip.add(look.scale(1.4));
                VfxKit.converge(point, c, 0.8, 12, 2);
                VfxKit.closing(point, look, c, 1.4f, 0.2f, 10, 2, 4);
            });
            // A revealing charm: a pale wave sweeps out over the ground.
            case "revelio" -> {
                Vec3 ground = VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2);
                Vec3 point = ground != null ? ground : caster.position();
                VfxKit.slowRing(point, VfxKit.UP, c, 1.0f, 28.0f, 24, 0);
                VfxKit.slowRing(point, VfxKit.UP, c, 1.0f, 20.0f, 24, 6);
            }
            // Film (Prisoner of Azkaban): a crack and the boggart turns ridiculous. A burst of colour from the wand.
            case "riddikulus" -> VfxKit.atTip(cid, 2, (who, tip) -> {
                for (int col : CONFETTI) {
                    VfxKit.gust(tip, look, col, 4, 0.3, 0.12);
                }
                VfxKit.thinRing(tip, look, c, 0.2f, 1.6f, 6, 0);
            });
            // A freezing charm round the caster: frost blooming on the ground, a cold ring, ice rising, a breath.
            case "frigora" -> {
                Vec3 ground = VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2);
                if (ground != null) {
                    frost(ground, 2.8f);
                    VfxKit.shockwave(ground, VfxKit.UP, c, 0.5f, 4.0f, 9, 0);
                    VfxKit.rise(ground, FROST, 1.2, 14, 0.06);
                }
            }
            // Film (Prisoner of Azkaban, the pitch): the fall slows. Rings spreading slowly, motes hanging.
            case "arresto_momentum" -> {
                Vec3 feet = caster.position();
                for (int k = 0; k < 3; k++) {
                    VfxKit.slowRing(feet.add(0, 0.05, 0), VfxKit.UP, c, 0.6f, 3.0f, 40, k * 8);
                }
                VfxKit.sink(feet.add(0, 0.4, 0), c, 1.2, 30);
            }
            // Canon (Deathly Hallows, the beaded bag): space folded in. Violet rings closing into the hand.
            case "capacious_extremis" -> VfxKit.atTip(cid, 2, (who, tip) -> {
                VfxKit.closing(tip, look, c, 1.2f, 0.1f, 8, 3, 3);
                VfxKit.converge(tip, c, 0.6, 10, 2);
            });
            // A sealing charm: a small turning circle at the feet and a ring.
            case "claustra_reverto" -> {
                Vec3 ground = VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2);
                if (ground != null) {
                    VfxKit.runeCircle(ground, c, 1.8f, 20, 3, 8, 3.0f);
                    VfxKit.thinRing(ground, VfxKit.UP, c, 0.4f, 2.2f, 8, 0);
                }
            }
            // Film (Prisoner of Azkaban, the lake): a wave of silver light rolls out and drives the dark back.
            case "expecto_patronum" -> {
                Vec3 ground = VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2);
                Vec3 point = ground != null ? ground : caster.position();
                VfxKit.shockwave(point.add(0, 0.6, 0), VfxKit.UP, SILVER, 1.0f, 12.0f, 16, 2);
                VfxKit.thinRing(point.add(0, 0.6, 0), VfxKit.UP, SILVER, 0.8f, 9.0f, 14, 5);
                VfxKit.atTip(cid, 2, (who, tip) -> VfxKit.mist(tip.add(look.scale(0.5)), SILVER, 5, 0.3, 0.01));
            }
            // Film: the Imperius Curse shows almost nothing. A faint pale wisp off the wand, nothing more.
            case "imperio" -> VfxKit.atTip(cid, 2, (who, tip) -> VfxEmitters.start((level, rnd, age) -> {
                Vec3 t = WandTipTracker.resolve(who, 1.0f);
                VfxKit.moteAt(level, t, look.scale(0.05).add(0, 0.01, 0), 0xFFE6DCF5);
            }, 12, 3));
            // Film (Fantastic Beasts): the Obscurus erupts as black roiling smoke; the ground cracks and glows;
            // ash falls after.
            case "obscurus_surge" -> {
                Vec3 centre = caster.position().add(0, 1.0, 0);
                VfxKit.mist(centre, OBSCURUS_BLACK, 14, 0.9, 0.04);
                obscurusCrack(VfxKit.groundBelow(caster.position().add(0, 0.2, 0), 2), 3.2f);
                VfxEmitters.start((level, rnd, age) -> VfxKit.moteAt(level,
                        centre.add(rnd.nextGaussian() * 1.2, 1.0 + rnd.nextDouble(), rnd.nextGaussian() * 1.2),
                        new Vec3(0, -0.03, 0), 0xFF3A3640), 40, 1);
            }
            default -> { }
        }
    }

    // ── shared marks ───────────────────────────────────────────────────────────────────────────

    /** Frost blooming on the ground under {@code pos}, with a breath of cold over it. */
    static void frost(Vec3 pos, float size) {
        Vec3 ground = VfxKit.groundBelow(pos.add(0, 0.3, 0), 3);
        if (ground == null) {
            return;
        }
        VfxKit.mark(VfxQuads.FROST, ground, VfxKit.UP, FROST, size, 220, 80);
        VfxKit.mist(ground.add(0, 0.2, 0), ICE_MIST, 3, 0.4, 0.0);
    }

    /** The ground torn open: black fissures over an ember glow that dies first. */
    static void obscurusCrack(@Nullable Vec3 ground, float size) {
        if (ground == null) {
            return;
        }
        VfxQuads.spawn(VfxQuads.CRACK, VfxQuads.Look.GLOW, ground, VfxKit.UP, size, size * 1.08f, EMBER, 70, 0, 50,
                0f, VfxQuads.Growth.LAND, 0);
        VfxKit.mark(VfxQuads.CRACK, ground.add(0, 0.004, 0), VfxKit.UP, OBSCURUS_BLACK, size, 240, 80);
    }

    /** Test seam. */
    static VfxQuads.@Nullable Quad creepOf(int casterId) {
        return CREEP.get(casterId);
    }
}
