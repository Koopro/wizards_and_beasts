package at.koopro.wizardsandbeasts.event.spell;

import at.koopro.wizardsandbeasts.network.spell.SpellVfxShapeS2CPayload;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ExpectoPatronumAuraHandler {
    private static final Map<UUID, Long> AURA_UNTIL = new ConcurrentHashMap<>();
    private static final float AURA_RADIUS = 6.0f;
    private static final int AURA_TICK_INTERVAL = 10;
    private static final int AURA_COLOR = 0xFFCCDDFF;

    private ExpectoPatronumAuraHandler() {}

    public static void activate(ServerPlayer caster, long untilTick) {
        AURA_UNTIL.put(caster.getUUID(), untilTick);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (AURA_UNTIL.isEmpty()) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        AURA_UNTIL.entrySet().removeIf(e -> now >= e.getValue());

        for (ServerPlayer caster : event.getServer().getPlayerList().getPlayers()) {
            Long until = AURA_UNTIL.get(caster.getUUID());
            if (until == null || now >= until) continue;
            if (!caster.isAlive()) {
                // The aura is the caster's Patronus, and it goes with them — not on around the body, and not
                // onto the respawned one, which shares the id this map is keyed by.
                AURA_UNTIL.remove(caster.getUUID());
                continue;
            }
            if (!(caster.level() instanceof ServerLevel level)) continue;

            if (now % AURA_TICK_INTERVAL == 0) {
                applyAuraPulse(level, caster);
            }
            renderAuraParticles(level, caster);
        }
    }

    private static void applyAuraPulse(ServerLevel level, ServerPlayer caster) {
        AABB box = caster.getBoundingBox().inflate(AURA_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive())) {
            boolean antiDark = isDarkAligned(target);
            if (!antiDark) continue;

            Vec3 push = target.position().subtract(caster.position());
            if (push.lengthSqr() < 1.0e-4) continue;
            Vec3 force = push.normalize().scale(0.9);
            target.push(force.x, 0.18, force.z);
            target.hurtMarked = true;
            target.hurt(level.damageSources().magic(), 4.0f);
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    private static boolean isDarkAligned(LivingEntity entity) {
        if (entity.isInvertedHealAndHarm()) return true;
        if (entity instanceof ServerPlayer sp) {
            PlayerHeritageData data = sp.getData(ModAttachments.HERITAGE_DATA.get());
            return ObscurialRules.isObscurial(data) && ObscurialRules.isDarkForm(data);
        }
        return false;
    }

    private static void renderAuraParticles(ServerLevel level, ServerPlayer caster) {
        // One ring packet every other tick, sixteen points, instead of eight single-particle packets every tick
        // (160 a second per observer, audit 2026-11-15). Same particles a second; the ring still turns.
        long time = level.getGameTime();
        if (time % 2 != 0) {
            return;
        }
        int rgb = AURA_COLOR & 0x00FFFFFF;
        Vec3 centre = new Vec3(caster.getX(), caster.getY() + caster.getBbHeight() * 0.8, caster.getZ());
        SpellVfxShapeS2CPayload.ring(level, new DustParticleOptions(rgb, 1.0f), centre, new Vec3(0, 1, 0),
                AURA_RADIUS * 0.55, 16, (float) (time * 0.25));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        AURA_UNTIL.remove(sp.getUUID());
    }
}
