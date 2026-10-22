package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A gaze into the Mirror of Erised, server side: who is looking, what the Mirror shows them, what it costs, and
 * what it remembers. The block only routes the click here; the choices are {@link ErisedSelector}'s.
 */
@NullMarked
public final class ErisedService {

    public enum Result { VISION, REFLECTION, DISABLED }

    private ErisedService() {}

    /** One gaze. Sends the gazer what they saw and returns what kind of gaze it was. */
    public static Result gaze(ServerPlayer player, BlockPos mirror) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return Result.DISABLED;
        }
        long today = SpellPractice.day(player.level().getServer().overworld().getGameTime());
        ErisedHistory history = player.getData(ModAttachments.ERISED_HISTORY.get());
        int gazes = history.gazesOn(today);
        if (ErisedSelector.onlyReflection(gazes)) {
            PacketDistributor.sendToPlayer(player,
                    new ErisedVisionS2CPayload(Optional.empty(), Optional.empty(), 0, false));
            return Result.REFLECTION;
        }

        long seed = seed(player, today, gazes);
        Gazer gazer = gazer(player);
        ErisedSelector.Choice choice = ErisedSelector.pickVision(gazer, ErisedVisions.all(), history.recent(), seed);
        ClueCandidate clue = ErisedSelector.mayGiveClue(gazes)
                ? ErisedSelector.pickClue(ErisedClues.candidates(player), history, seed)
                : null;
        player.setData(ModAttachments.ERISED_HISTORY.get(), history.gazed(today,
                choice == null ? null : choice.id().toString(), clue == null ? null : clue.key()));

        ErisedSelector.Pull pull = ErisedSelector.pullAfter(gazes);
        if (pull.any()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, pull.seconds() * 20, pull.amplifier()));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, pull.seconds() * 20, pull.amplifier()));
        }
        player.level().playSound(null, mirror, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 0.6f);
        PacketDistributor.sendToPlayer(player, new ErisedVisionS2CPayload(
                Optional.ofNullable(choice).map(c -> text(c.vision(), gazer)),
                Optional.ofNullable(clue).map(ClueCandidate::text),
                ErisedSelector.GAZES_PER_DAY - gazes - 1, pull.any()));
        return choice == null ? Result.REFLECTION : Result.VISION;
    }

    /** The vision's line, its {@code %s} filled where it asks for one. */
    static Component text(ErisedVision vision, Gazer gazer) {
        return vision.arg()
                .flatMap(arg -> arg.value(gazer))
                .map(value -> (Component) Component.translatable(vision.text(), value))
                .orElseGet(() -> Component.translatable(vision.text()));
    }

    /** Different per player, per day and per gaze, and the same if the same gaze were asked twice. */
    static long seed(ServerPlayer player, long today, int gazes) {
        return player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits()
                ^ (today * 0x9E3779B97F4A7C15L) ^ gazes;
    }

    /** The gazer, read from the player's own records. */
    public static Gazer gazer(ServerPlayer player) {
        PlayerHeritageData heritage = HeritageAPI.getData(player);
        Set<MagicalCondition> conditions = EnumSet.noneOf(MagicalCondition.class);
        for (MagicalCondition condition : MagicalCondition.values()) {
            if (heritage.hasCondition(condition)) {
                conditions.add(condition);
            }
        }
        CompanionRoster roster = player.getData(ModAttachments.COMPANION_ROSTER.get());
        List<String> lost = roster.entries().values().stream()
                .filter(e -> !e.alive())
                .sorted(Comparator.comparingLong(CompanionRoster.Entry::lastSeenDay).reversed())
                .map(e -> e.name().isBlank() ? e.species().getPath() : e.name())
                .toList();
        int creatures = (int) BestiaryDataHelper.getData(player).tiers().values().stream()
                .filter(t -> t.atLeast(DiscoveryTier.OBSERVED)).count();
        return new Gazer(HeritageAPI.getPlayerHeritage(player), HeritageAPI.getPlayerHeritageVariant(player),
                conditions, lost, player.getData(ModAttachments.SPELL_DATA.get()).getKnownSpells().size(), creatures,
                player.getData(ModAttachments.PROFESSION_DATA.get()).profession(),
                new CareerProgression.ServerContext(player, PlayerCareerProgress.EMPTY.track("erised")));
    }
}
