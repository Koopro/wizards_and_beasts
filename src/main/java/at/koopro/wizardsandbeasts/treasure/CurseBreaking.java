package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.Map;

/**
 * Breaking a curse with one's own wand, and what an unbroken curse does to whoever carries it.
 *
 * <p><b>Reading.</b> Revelio cast with an artefact in the other hand reads its curse and depth — the free, skilled
 * alternative to a Gringotts appraisal.
 *
 * <p><b>Breaking.</b> The curse's counter ({@link CurseKind#counterSpell()}) cast with the artefact in hand lifts
 * one layer; the last layer is a {@code curse_broken} activity, which a Gringotts standing and the Curse-Breaker
 * career both count. Casting the counter to a <em>different</em> curse is a failed attempt: the curse flares
 * ({@link #backlash}). Any other spell is not an attempt and does nothing to the artefact — a wizard fighting with
 * treasure in the off hand is not punished for it.
 *
 * <p><b>Carrying.</b> Every five seconds each kind of curse carried lays its hazard once ({@link #tickHazards}),
 * however many such artefacts are in the pack.
 *
 * <p>Off with {@link Module#GRINGOTTS}, which owns the treasure.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class CurseBreaking {

    /** Ticks between hazard pulses. */
    static final int HAZARD_PERIOD = 100;

    private CurseBreaking() {}

    /** What one counter-spell or Revelio cast did to the artefact in hand. */
    public enum Outcome { NONE, IDENTIFIED, LAYER_LIFTED, BROKEN, BACKLASH }

    /**
     * The pure rule: {@code spell} cast at {@code artefact}. Returns the outcome; {@link #apply} turns it into the
     * new artefact state.
     */
    public static Outcome judge(TreasureArtefact artefact, String spellId) {
        if (!artefact.cursed()) {
            return Outcome.NONE;
        }
        String bare = CurseKind.bare(spellId);
        if (bare.equals("revelio")) {
            return artefact.identified() ? Outcome.NONE : Outcome.IDENTIFIED;
        }
        CurseKind countered = CurseKind.counteredBy(bare);
        if (countered == null) {
            return Outcome.NONE;
        }
        if (countered != artefact.curse().orElseThrow()) {
            return Outcome.BACKLASH;
        }
        return artefact.layers() <= 1 ? Outcome.BROKEN : Outcome.LAYER_LIFTED;
    }

    public static TreasureArtefact apply(TreasureArtefact artefact, Outcome outcome) {
        return switch (outcome) {
            case IDENTIFIED -> artefact.identify();
            case LAYER_LIFTED, BROKEN -> artefact.stripLayer();
            case NONE, BACKLASH -> artefact;
        };
    }

    /** Spell hook ({@code SpellCastService}, after a successful cast). A no-op unless an artefact is in hand. */
    public static void onSpellCast(ServerPlayer player, String spellId) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return;
        }
        InteractionHand hand = TreasureArtefacts.handHolding(player);
        if (hand == null) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        TreasureArtefact artefact = TreasureArtefacts.of(stack);
        if (artefact == null) {
            return;
        }
        Outcome outcome = judge(artefact, spellId);
        if (outcome == Outcome.NONE) {
            return;
        }
        TreasureArtefact next = apply(artefact, outcome);
        TreasureArtefacts.set(stack, next);
        ServerLevel level = player.level();
        switch (outcome) {
            case IDENTIFIED -> PlayerFeedback.actionBar(player, Component.translatable(
                    "treasure.wizards_and_beasts.revealed", artefact.curse().orElseThrow().displayName(),
                    artefact.layers(), CurrencyHelper.formatFromKnuts(artefact.valueKnuts()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            case LAYER_LIFTED -> {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
                        0.8f, 1.2f);
                PlayerFeedback.actionBar(player, Component.translatable("treasure.wizards_and_beasts.layer_lifted",
                        next.layers()).withStyle(ChatFormatting.AQUA));
            }
            case BROKEN -> {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                        1.0f, 1.0f);
                PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                        Component.translatable("treasure.wizards_and_beasts.curse_broken.title"),
                        Component.translatable("treasure.wizards_and_beasts.curse_broken.body",
                                artefact.curse().orElseThrow().displayName()));
                CareerProgression.record(player, ActivityType.CURSE_BROKEN, artefact.curse().orElseThrow().id());
            }
            case BACKLASH -> backlash(player, artefact);
            case NONE -> { }
        }
    }

    /** The wrong counter: the curse flares — its hazard at once, and a sting that grows with its depth. */
    static void backlash(ServerPlayer player, TreasureArtefact artefact) {
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.6f);
        player.hurtServer(level, level.damageSources().magic(), 1.0f + artefact.layers());
        hazard(player, artefact.curse().orElseThrow());
        PlayerFeedback.actionBar(player, Component.translatable("treasure.wizards_and_beasts.backlash")
                .withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % HAZARD_PERIOD == 0) {
            tickHazards(player);
        }
    }

    /** Lays each carried curse's hazard once. Creative and spectating players are spared. */
    public static void tickHazards(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS) || player.isCreative() || player.isSpectator()
                || !player.isAlive()) {
            return;
        }
        Map<CurseKind, Integer> carried = new EnumMap<>(CurseKind.class);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            TreasureArtefact artefact = TreasureArtefacts.of(player.getInventory().getItem(slot));
            if (artefact != null && artefact.cursed()) {
                carried.merge(artefact.curse().orElseThrow(), artefact.layers(), Math::max);
            }
        }
        carried.keySet().forEach(curse -> hazard(player, curse));
    }

    static void hazard(ServerPlayer player, CurseKind curse) {
        int duration = HAZARD_PERIOD + 20;
        switch (curse) {
            case JINX -> {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, duration, 0, false, true));
            }
            case FLAGRANTE -> {
                if (!player.isInWaterOrRain()) {
                    player.igniteForSeconds(2.0f);
                }
            }
            case SEALING -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, true));
            case WITHERING -> player.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0, false, true));
        }
    }
}
