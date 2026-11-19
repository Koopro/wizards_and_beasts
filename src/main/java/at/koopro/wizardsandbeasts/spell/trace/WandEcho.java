package at.koopro.wizardsandbeasts.spell.trace;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCasework;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a wand remembers, and the two spells that read the past back.
 *
 * <ul>
 *   <li>Every successful cast is written onto the wand that cast it ({@link #record}): its last
 *       {@link #DEPTH} spells, newest first. It travels with the wand, dropped, traded or confiscated.</li>
 *   <li><b>Prior Incantato</b> ({@link #priorIncantato}) reads them back from the wand in the hand of whoever
 *       the caster is looking at, from a wand lying where the caster looks, or else from the caster's own
 *       off-hand: a smoky echo of the last spell rises from it (<i>Goblet of Fire</i> ch. 9, Amos Diggory
 *       and the Dark Mark).</li>
 *   <li><b>Appare Vestigium</b> ({@link #appareVestigium}) shows gold traces where magic the Ministry heard
 *       of was done nearby in the last few days (<i>The Crimes of Grindelwald</i>, Newt in Paris); for an
 *       Auror on a case it also files the scene, as Revelio does.</li>
 * </ul>
 */
@NullMarked
public final class WandEcho {

    public static final int DEPTH = 3;
    public static final double VESTIGIUM_RADIUS = 32.0;
    /** Three Minecraft days. */
    public static final long VESTIGIUM_TICKS = 72000L;

    private WandEcho() {}

    /** Writes a cast onto the wand in the caster's main hand. No-op when it is not a wand. */
    public static void record(ServerPlayer caster, String spellId) {
        ItemStack wand = caster.getMainHandItem();
        if (!WandHelper.isWand(wand)) {
            return;
        }
        List<String> echo = new ArrayList<>(wand.getOrDefault(ModDataComponents.WAND_ECHO.get(), List.of()));
        echo.add(0, spellId);
        while (echo.size() > DEPTH) {
            echo.remove(echo.size() - 1);
        }
        wand.set(ModDataComponents.WAND_ECHO.get(), List.copyOf(echo));
    }

    public static List<String> echo(ItemStack wand) {
        return wand.getOrDefault(ModDataComponents.WAND_ECHO.get(), List.of());
    }

    public static boolean priorIncantato(ServerLevel level, ServerPlayer caster, Spell spell) {
        ItemStack wand = wandInView(level, caster);
        if (wand == null) {
            PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.prior_incantato.no_wand"));
            return false;
        }
        List<String> echo = echo(wand);
        if (echo.isEmpty()) {
            PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.prior_incantato.silent"));
            return true;
        }
        Spell last = Spells.byId(echo.get(0));
        int colour = last != null ? last.getColor() : 0xAAAAAA;
        Vec3 puff = caster.getEyePosition().add(caster.getLookAngle().scale(2.0));
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, puff.x, puff.y, puff.z,
                20, 0.2, 0.4, 0.2, 0.01);
        level.sendParticles(at.koopro.wizardsandbeasts.registry.ModParticles.tinted(at.koopro.wizardsandbeasts.spell.core.SpellFamily.ARCANE, colour), puff.x, puff.y + 0.5, puff.z,
                24, 0.3, 0.5, 0.3, 0.01);
        Component names = Component.empty();
        for (int i = 0; i < echo.size(); i++) {
            if (i > 0) {
                names = names.copy().append(", ");
            }
            names = names.copy().append(MinistryTrace.spellName(echo.get(i)));
        }
        PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.prior_incantato.echo", names));
        return true;
    }

    /** The wand the caster means: in the hand of whoever they look at, lying where they look, or their own off-hand. */
    static @Nullable ItemStack wandInView(ServerLevel level, ServerPlayer caster) {
        LivingEntity faced = at.koopro.wizardsandbeasts.spell.lib.SpellTargetHelper.findTargetedEntity(level, caster, 8.0f);
        if (faced != null && WandHelper.isWand(faced.getMainHandItem())) {
            return faced.getMainHandItem();
        }
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        ItemEntity best = null;
        double bestScore = 0.9;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(6.0),
                e -> WandHelper.isWand(e.getItem()))) {
            double score = item.position().subtract(eye).normalize().dot(look);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        if (best != null) {
            return best.getItem();
        }
        return WandHelper.isWand(caster.getOffhandItem()) ? caster.getOffhandItem() : null;
    }

    public static boolean appareVestigium(ServerLevel level, ServerPlayer caster, Spell spell) {
        int filed = MinistryCasework.examine(caster);
        MinistryCaseData data = MinistryCaseData.get(level.getServer());
        long now = MinistryTrace.now(level.getServer());
        List<Incident> traces = data.incidentsNear(level.dimension().identifier(), caster.blockPosition(),
                VESTIGIUM_RADIUS, now - VESTIGIUM_TICKS);
        for (Incident incident : traces) {
            Vec3 p = incident.pos().getCenter();
            level.sendParticles(at.koopro.wizardsandbeasts.particle.MagicMotes.gold(), p.x, p.y + 0.5, p.z,
                    40, 0.3, 0.9, 0.3, 0.005);
        }
        if (traces.isEmpty()) {
            PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.appare_vestigium.nothing"));
            return filed > 0;
        }
        Incident freshest = traces.get(0);
        PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.appare_vestigium.traces",
                traces.size(), MinistryTrace.spellName(freshest.spellId())));
        return true;
    }
}
