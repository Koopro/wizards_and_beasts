package at.koopro.wizardsandbeasts.spell.protego;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Shield Charm says out loud: particles, sounds and the action-bar line.
 *
 * <p>Split out of the entity and the spell so the state machine reads as state and the presentation
 * reads as presentation, and so the charge-up, the raise and the four different endings can be told
 * apart at a glance — which is the whole point of the feedback pass.
 *
 * <p>Server-side. Particles and sounds are broadcast to everyone nearby on purpose: a Horribilis
 * being charged is a thing an opponent should be able to see coming and act on.
 */
public final class ProtegoFeedback {

    /** Beat of the charge hum. Four times a second reads as continuous without being a drone. */
    private static final int HUM_INTERVAL_TICKS = 5;

    private ProtegoFeedback() {}

    // ── charging ────────────────────────────────────────────────────────────────────────────────

    /** The wand answers: a rising chime and a flare of the new tier's colour as each shape is reached. */
    public static void chargeStep(ServerLevel level, ServerPlayer caster, ProtegoTier tier, boolean planting) {
        level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_CHARGE.get(), SoundSource.PLAYERS,
                0.55f, 0.8f + tier.index() * 0.22f);
        ring(level, tint(tier), caster.position().add(0.0, 1.0, 0.0), 0.8 + tier.index() * 0.2, 10);
        announce(caster, chargeLine(tier, planting).withStyle(ChatFormatting.AQUA));
    }

    /** Held past what the caster can actually hold: say which wall they hit, once, as they hit it. */
    public static void chargeCapped(ServerLevel level, ServerPlayer caster, ProtegoRules.Limit limit) {
        Component line = switch (limit) {
            case PROFICIENCY -> Component.translatable("spell.wizards_and_beasts.protego.limit.proficiency");
            case DARK_ARTS -> Component.translatable("spell.wizards_and_beasts.protego.limit.dark_arts");
            case NONE -> null;
        };
        if (line == null) {
            return;
        }
        level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_STRAIN.get(), SoundSource.PLAYERS,
                0.5f, 0.9f);
        announce(caster, line.copy().withStyle(ChatFormatting.GOLD));
    }

    /**
     * The charm gathering: motes circling the caster's wand hand, and — while they sneak with a
     * plantable shape ready — the footprint the dome will take on the ground.
     */
    public static void chargeAmbient(ServerLevel level, ServerPlayer caster, ProtegoTier tier,
                                     int heldTicks, boolean planting, float progress) {
        // A hum under the chimes: pitch and volume climb with the fill towards the next shape, so the
        // ear tracks the ramp and not just the four thresholds. Quiet — it plays four times a second.
        if (heldTicks % HUM_INTERVAL_TICKS == 0) {
            float climb = (tier.index() + progress) / ProtegoTier.values().length;
            level.playSound(null, caster.blockPosition(), ModSounds.PROTEGO_CHARGE.get(), SoundSource.PLAYERS,
                    0.12f + 0.18f * climb, 0.7f + 0.75f * climb);
        }
        int interval = Config.perfProfile == Config.PerfProfile.LOW ? 4 : 2;
        if (heldTicks % interval == 0) {
            double radius = 0.7 + tier.index() * 0.18;
            double angle = heldTicks * 0.45;
            Vec3 centre = caster.position().add(0.0, 1.1, 0.0);
            int motes = Math.max(1, Math.round(density() * (1 + tier.index())));
            for (int i = 0; i < motes; i++) {
                double a = angle + (i * Math.PI * 2.0 / motes);
                level.sendParticles(tint(tier),
                        centre.x + Math.cos(a) * radius, centre.y + Math.sin(angle * 0.5) * 0.15,
                        centre.z + Math.sin(a) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (planting && heldTicks % 5 == 0) {
            ring(level, tint(tier), caster.position().add(0.0, 0.1, 0.0), tier.radius(), 16);
        }
        // Re-stated on a slow beat: the action bar times out, and the caster can change their mind
        // about planting halfway through a hold.
        if (heldTicks % 10 == 1) {
            announce(caster, chargeLine(tier, planting).withStyle(ChatFormatting.AQUA));
        }
    }

    private static MutableComponent chargeLine(ProtegoTier tier, boolean planting) {
        return Component.translatable(
                planting ? "spell.wizards_and_beasts.protego.charging_planted"
                        : "spell.wizards_and_beasts.protego.charging",
                Component.translatable(tier.nameKey()));
    }

    // ── raising ─────────────────────────────────────────────────────────────────────────────────

    /** The cast landing: tier voice, a shell of light at the ward's real radius, and the raise pulse. */
    public static void raise(ServerLevel level, ServerPlayer caster, ProtegoShieldEntity shield) {
        ProtegoTier tier = shield.tier();
        BlockPos at = caster.blockPosition();
        level.playSound(null, at, ModSounds.PROTEGO_RAISE.get(), SoundSource.PLAYERS,
                0.75f, 1.15f - tier.index() * 0.08f);
        switch (tier) {
            case TOTALUM -> level.playSound(null, at, ModSounds.PROTEGO_TOTALUM_RAISE.get(),
                    SoundSource.PLAYERS, 0.9f, 1.0f);
            case MAXIMA -> level.playSound(null, at, ModSounds.PROTEGO_MAXIMA_RAISE.get(),
                    SoundSource.PLAYERS, 1.0f, 0.95f);
            case HORRIBILIS -> {
                level.playSound(null, at, ModSounds.PROTEGO_MAXIMA_RAISE.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
                level.playSound(null, at, ModSounds.PROTEGO_HORRIBILIS_ABSORB.get(), SoundSource.PLAYERS, 0.8f, 0.7f);
            }
            default -> { /* the quick parry has only its own raise */ }
        }
        if (shield.isPlanted()) {
            level.playSound(null, at, ModSounds.PROTEGO_PLANT.get(), SoundSource.PLAYERS, 0.9f, 0.85f);
            ring(level, tint(tier), shield.position().add(0.0, 0.1, 0.0), tier.radius(), 24);
        }
        shell(level, shield, 1.0f);
        raisePulse(level, caster, shield);
        announce(caster, Component.translatable(
                shield.isPlanted() ? "spell.wizards_and_beasts.protego.planted"
                        : "spell.wizards_and_beasts.protego.raised",
                Component.translatable(tier.nameKey()),
                Math.round(shield.getMaxIntegrity())).withStyle(ChatFormatting.AQUA));
    }

    /**
     * The shove a shield gives as it snaps open: nearby projectiles and hostiles are pushed off it
     * and fire at the caster's feet is smothered.
     *
     * <p>Allies are skipped, which the old version did not do — an ally-covering ward whose first
     * act was to throw its allies out of it was working against itself.
     */
    private static void raisePulse(ServerLevel level, ServerPlayer caster, ProtegoShieldEntity shield) {
        double reach = Math.min(4.0, shield.tier().radius() + 0.5);
        float strength = 0.9f + shield.tier().index() * 0.15f;
        Vec3 centre = caster.getBoundingBox().getCenter();
        AABB area = caster.getBoundingBox().inflate(reach);
        for (Entity entity : level.getEntities(caster, area,
                e -> e instanceof Projectile || e instanceof LivingEntity)) {
            if (entity instanceof LivingEntity living && ProtegoWardManager.isAlly(caster, living)) {
                continue;
            }
            Vec3 away = entity.getBoundingBox().getCenter().subtract(centre);
            if (away.lengthSqr() < 1.0e-4) {
                continue;
            }
            SpellHelper.applyKnockback(entity, away, strength);
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(centre).offset(-2, -1, -2),
                BlockPos.containing(centre).offset(2, 2, 2))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.removeBlock(pos, false);
            }
        }
    }

    // ── while it stands ─────────────────────────────────────────────────────────────────────────

    /** A planted dome breathes its ring; a failing one sheds sparks. Cheap, and on a slow beat. */
    public static void ambient(ServerLevel level, ProtegoShieldEntity shield, int ticksAlive) {
        ProtegoTier tier = shield.tier();
        if (shield.isPlanted() && ticksAlive % 20 == 0) {
            ring(level, tint(tier), shield.position().add(0.0, 0.1, 0.0), tier.radius(), 16);
        }
        if (shield.integrityFraction() <= ProtegoRules.LOW_INTEGRITY_FRACTION && ticksAlive % 5 == 0) {
            Vec3 centre = shield.centre();
            double a = ticksAlive * 0.7;
            double r = tier.radius() * 0.85;
            level.sendParticles(tint(tier),
                    centre.x + Math.cos(a) * r, centre.y + level.random.nextDouble() * 0.6,
                    centre.z + Math.sin(a) * r,
                    2, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /** A spell meeting the ward: colour of the shield, pitch of how much is left in it. */
    public static void spellImpact(ServerLevel level, ProtegoShieldEntity shield, Vec3 at, boolean swallowed) {
        ProtegoTier tier = shield.tier();
        if (swallowed) {
            level.sendParticles(ModParticles.PROTEGO_SHATTER.get(), at.x, at.y, at.z,
                    scaled(14), 0.2, 0.2, 0.2, 0.03);
            level.playSound(null, BlockPos.containing(at), ModSounds.PROTEGO_HORRIBILIS_ABSORB.get(),
                    SoundSource.PLAYERS, 0.85f, 0.92f);
            return;
        }
        level.sendParticles(tint(tier), at.x, at.y, at.z, scaled(16), 0.15, 0.15, 0.15, 0.02);
        level.playSound(null, BlockPos.containing(at), ModSounds.PROTEGO_BLOCK.get(), SoundSource.PLAYERS,
                0.8f, blockPitch(shield));
    }

    /** A blow the ward took for somebody. Quieter than a spell; it happens far more often. */
    public static void blowImpact(ServerLevel level, ProtegoShieldEntity shield, Vec3 at) {
        level.sendParticles(tint(shield.tier()), at.x, at.y, at.z, scaled(8), 0.12, 0.12, 0.12, 0.01);
        level.playSound(null, BlockPos.containing(at), ModSounds.PROTEGO_BLOCK.get(), SoundSource.PLAYERS,
                0.6f, blockPitch(shield));
    }

    /** Crossing into the last third of the pool: the ward starts to sing, and the caster is told. */
    public static void strain(ServerLevel level, ProtegoShieldEntity shield, Vec3 at) {
        level.playSound(null, BlockPos.containing(at), ModSounds.PROTEGO_STRAIN.get(), SoundSource.PLAYERS,
                0.9f, 0.75f);
        shell(level, shield, 0.4f);
        if (shield.getServerCaster(level) instanceof ServerPlayer caster) {
            announce(caster, Component.translatable("spell.wizards_and_beasts.protego.failing")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ── endings ─────────────────────────────────────────────────────────────────────────────────

    /** Spent: glass, a shockwave of fragments, and a line the caster cannot miss. */
    public static void breach(ServerLevel level, ProtegoShieldEntity shield) {
        ProtegoTier tier = shield.tier();
        Vec3 centre = shield.centre();
        double spread = Math.max(1.0, tier.radius() * 0.5);
        level.sendParticles(ModParticles.PROTEGO_SHATTER.get(), centre.x, centre.y, centre.z,
                scaled(30 + tier.index() * 12), spread, spread * 0.6, spread, 0.05);
        shell(level, shield, 1.0f);
        level.playSound(null, BlockPos.containing(centre), ModSounds.PROTEGO_SHATTER.get(), SoundSource.PLAYERS,
                1.2f, 0.9f - tier.index() * 0.07f);
    }

    /** Ran out, replaced, or left behind: it simply goes, and costs nothing. */
    public static void fade(ServerLevel level, ProtegoShieldEntity shield, ProtegoShieldEntity.Collapse cause) {
        Vec3 centre = shield.centre();
        level.sendParticles(tint(shield.tier()), centre.x, centre.y, centre.z,
                scaled(10), shield.tier().radius() * 0.4, 0.4, shield.tier().radius() * 0.4, 0.01);
        // A shield the caster deliberately replaced needs no sound of its own; the new one is loud enough.
        if (cause != ProtegoShieldEntity.Collapse.REPLACED) {
            level.playSound(null, BlockPos.containing(centre), ModSounds.PROTEGO_FADE.get(), SoundSource.PLAYERS,
                    0.55f, 1.05f);
        }
    }

    /** The second after an ending: fragments falling out of a breach, nothing after a fade. */
    public static void collapseTrail(ServerLevel level, ProtegoShieldEntity shield, int collapseTicks) {
        if (!shield.collapseCause().isBreach() || collapseTicks % 2 != 0 || collapseTicks > 12) {
            return;
        }
        Vec3 centre = shield.centre();
        double r = shield.tier().radius() * 0.8;
        level.sendParticles(ModParticles.PROTEGO_SHATTER.get(),
                centre.x, centre.y, centre.z, scaled(4), r, 0.5, r, 0.02);
    }

    // ── small shared pieces ─────────────────────────────────────────────────────────────────────

    /** A sparse shell of motes on the ward's surface, so its real size is visible for a moment. */
    private static void shell(ServerLevel level, ProtegoShieldEntity shield, float intensity) {
        ProtegoTier tier = shield.tier();
        Vec3 centre = shield.centre();
        int points = Math.max(4, Math.round(scaled(tier.frontalOnly() ? 10 : 22) * intensity));
        for (int i = 0; i < points; i++) {
            // Fibonacci-ish spread over the sphere: even coverage without trigonometric clumping.
            double y = 1.0 - (i / (double) Math.max(1, points - 1)) * (tier.frontalOnly() ? 1.0 : 1.2);
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = i * 2.399963;
            Vec3 p = centre.add(Math.cos(theta) * r * tier.radius(), y * tier.radius(),
                    Math.sin(theta) * r * tier.radius());
            level.sendParticles(tint(tier), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void ring(ServerLevel level, ParticleOptions options, Vec3 centre,
                             double radius, int points) {
        int n = Math.max(4, Math.round(points * density()));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            level.sendParticles(options,
                    centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static SpellTintParticleOptions tint(ProtegoTier tier) {
        return new SpellTintParticleOptions(ModParticles.PROTEGO_DEFLECT.get(), tier.colour());
    }

    /** Rises as the pool empties — the ward sounds tighter the closer it is to going. */
    private static float blockPitch(ProtegoShieldEntity shield) {
        return 0.85f + (1.0f - shield.integrityFraction()) * 0.5f;
    }

    private static int scaled(int count) {
        return Math.max(1, Math.round(count * density()));
    }

    private static float density() {
        return switch (Config.perfProfile) {
            case LOW -> 0.5f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.4f;
        };
    }

    private static void announce(ServerPlayer caster, Component line) {
        caster.displayClientMessage(line, true);
    }
}
