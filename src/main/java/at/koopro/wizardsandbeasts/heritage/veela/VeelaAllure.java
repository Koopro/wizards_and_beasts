package at.koopro.wizardsandbeasts.heritage.veela;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageTransformService;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.stats.StatResistModifiers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A Veela's allure, as something that happens rather than a line on the character sheet.
 *
 * <p>Canon (Goblet of Fire): when the Veela danced, Harry's mind went "quite blank"; Ron and the men around him wanted
 * nothing but to impress them and would have jumped from the top box to do it. Fleur's allure turned heads down every
 * corridor, and made Ron blurt an invitation he could not take back. It is not a spell, it has no target, and it does
 * not make anyone a servant. What it reliably does is take the fight out of people.
 *
 * <p>So: the Veela lets the allure out (an ability on the wheel) and, for a while, the <b>people</b> around them cannot
 * bring themselves to raise a hand — the mod already has that state, {@code INFATUATION}, and this reuses it:
 * <ul>
 *   <li>other players who fail to resist cannot strike anyone (the existing Amortentia rule), and are told why;</li>
 *   <li>people-like mobs ({@code #wizards_and_beasts:allure_susceptible}: villagers, illagers, witches, piglins) drop
 *       whatever they were fighting and cannot take up another target until it passes.</li>
 * </ul>
 *
 * <p><b>Limits, all canon-shaped:</b> beasts, the undead and constructs do not care; another Veela is untouched; a
 * strong will throws it off (the same {@link StatResistModifiers#resistScalar} the Imperius and Legilimency use); it
 * reaches as far and lasts as long as the lineage is Veela ({@link #strength}); the harpy has no allure — fury replaced
 * it — so it cannot be used transformed; and the moment the Veela strikes anyone, the spell on everyone they entranced
 * breaks, because anger is exactly what turns a Veela's beauty into fire.
 */
@NullMarked
public final class VeelaAllure {

    /** People the allure can reach, beyond players. */
    public static final TagKey<EntityType<?>> SUSCEPTIBLE = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "allure_susceptible"));

    /** A lineage's reach and hold. */
    public record Strength(double radius, int durationTicks) {}

    /** Who each Veela currently has entranced, so their anger breaks exactly that and not an Amortentia. */
    private static final Map<UUID, Set<UUID>> ENTRANCED = new HashMap<>();

    private VeelaAllure() {}

    /**
     * How far a lineage's allure reaches and how long it holds. A full Veela empties a stand; a quarter-Veela turns
     * heads across a room.
     */
    public static @Nullable Strength strength(@Nullable HeritageVariant variant) {
        if (variant == null || !variant.hasTag("allure")) {
            return null;
        }
        return switch (variant) {
            case VEELA_FULL -> new Strength(12.0, 200);
            case VEELA_HALF -> new Strength(9.0, 140);
            default -> new Strength(6.0, 100);
        };
    }

    /**
     * Whether a mind resists: the roll against half the will-scaled resist chance. Pure, so the odds a player faces
     * can be stated in a test.
     *
     * @param roll         uniform in [0, 1)
     * @param resistScalar {@link StatResistModifiers#resistScalar}, 0.4 at no will to 1.0 at full
     */
    public static boolean resists(float roll, float resistScalar) {
        return roll < 0.5f * resistScalar;
    }

    /** Whether this player may let the allure out now: a Veela of an alluring lineage, not in harpy form. */
    public static boolean canAllure(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.HERITAGE)) {
            return false;
        }
        PlayerHeritageData data = HeritageAPI.getData(player);
        return data.getSelectedHeritage() == Heritage.VEELA && strength(data.getSelectedHeritageVariant()) != null
                && !HeritageTransformService.isTransformed(data);
    }

    /** Whether someone is a Veela, and so past the reach of another's allure. */
    static boolean isVeela(LivingEntity entity) {
        return entity instanceof ServerPlayer player
                && HeritageAPI.getData(player).getSelectedHeritage() == Heritage.VEELA;
    }

    /**
     * Lets the allure out.
     *
     * @return how many were entranced; {@code -1} when this player cannot allure at all
     */
    public static int allure(ServerPlayer veela) {
        if (!canAllure(veela) || !(veela.level() instanceof ServerLevel level)) {
            return -1;
        }
        Strength strength = strength(HeritageAPI.getData(veela).getSelectedHeritageVariant());
        Set<UUID> entranced = ENTRANCED.computeIfAbsent(veela.getUUID(), k -> new HashSet<>());
        entranced.clear();
        int count = 0;
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class,
                veela.getBoundingBox().inflate(strength.radius()), e -> e != veela && e.isAlive())) {
            if (near instanceof ServerPlayer player) {
                if (player.isSpectator() || isVeela(player)) {
                    continue;
                }
                if (resists(player.getRandom().nextFloat(), StatResistModifiers.resistScalar(player))) {
                    PlayerFeedback.actionBar(player, Component.translatable("veela.wizards_and_beasts.allure.resisted"));
                    continue;
                }
                entrance(player, strength);
                PlayerFeedback.toast(player, NoticeKind.WARN,
                        Component.translatable("veela.wizards_and_beasts.allure.entranced.title"),
                        Component.translatable("veela.wizards_and_beasts.allure.entranced.body", veela.getDisplayName()));
            } else if (near instanceof Mob mob && mob.getType().is(SUSCEPTIBLE)) {
                entrance(mob, strength);
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(veela);
            } else {
                continue;
            }
            entranced.add(near.getUUID());
            count++;
        }
        level.playSound(null, veela.getX(), veela.getY(), veela.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, veela.getX(), veela.getY() + 1.0, veela.getZ(),
                24, strength.radius() * 0.25, 0.6, strength.radius() * 0.25, 0.01);
        PlayerFeedback.actionBar(veela, Component.translatable("veela.wizards_and_beasts.allure.cast", count));
        return count;
    }

    private static void entrance(LivingEntity entity, Strength strength) {
        entity.addEffect(new MobEffectInstance(ModEffects.INFATUATION, strength.durationTicks(), 0, false, true, true));
    }

    /**
     * The Veela struck someone: anger breaks the allure on everyone they had entranced. Only their own — an
     * Amortentia someone else brewed is not theirs to lift.
     *
     * @return how many were released
     */
    public static int breakOnAnger(ServerPlayer veela) {
        Set<UUID> entranced = ENTRANCED.remove(veela.getUUID());
        if (entranced == null || entranced.isEmpty() || !(veela.level() instanceof ServerLevel level)) {
            return 0;
        }
        int released = 0;
        for (UUID id : entranced) {
            if (level.getEntity(id) instanceof LivingEntity entity && entity.hasEffect(ModEffects.INFATUATION)) {
                entity.removeEffect(ModEffects.INFATUATION);
                released++;
            }
        }
        return released;
    }

    /** Forget a Veela who has left. */
    public static void forget(UUID veela) {
        ENTRANCED.remove(veela);
    }
}
