package at.koopro.wizardsandbeasts.animagus;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.ability.AnimagusForms;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload listener for Animagus form definitions, mirroring {@code BestiaryEntryLoader}.
 * Wired in from the mod's {@code AddReloadListenerEvent} handler.
 *
 * <p>Also reports the gap between the mod's two Animagus id vocabularies on every reload — see
 * {@link #reportVocabularyGap}. The two have never lined up and nothing said so.
 */
@NullMarked
public final class AnimagusFormLoader extends SimpleJsonResourceReloadListener<AnimagusFormDefinition> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DIRECTORY = "animagus_forms";

    public AnimagusFormLoader() {
        super(AnimagusFormDefinition.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, AnimagusFormDefinition> jsonEntries,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        AnimagusFormRegistry.replaceAll(new HashMap<>(jsonEntries));
        reportVocabularyGap();
    }

    /**
     * Logs the mismatch between the forms a player can choose and the forms that have a definition.
     *
     * <p>The mod carries two Animagus id vocabularies: {@code AnimagusForms.IDS} is what a player can
     * select and what is written into their save ({@code animagus_cat}, {@code animagus_stag}, …), and
     * the datapack keys here are what carries the hitbox, attributes, capabilities and rig
     * ({@code wizards_and_beasts:cat}, {@code :falcon}, …). {@code AnimagusFormBinding} translates
     * between them, and for most of the roster it translates to nothing:
     *
     * <ul>
     *   <li>a <b>selectable form with no definition</b> is playable but gets no capabilities, no
     *       attribute block and no data-driven senses — it falls back entirely to the hardcoded switch
     *       in {@code AnimagusAbilityService};</li>
     *   <li>a <b>definition nothing can select</b> is content that loads, validates and is never
     *       reachable in game.</li>
     * </ul>
     *
     * <p>At {@code WARN} rather than {@code DEBUG} on purpose. Both halves are silent failures: nothing
     * crashes, nothing logs, the form simply does less than its file says. That is precisely the kind of
     * gap that survives for a year, and this is the one moment the mod has both lists in hand.
     */
    private static void reportVocabularyGap() {
        List<String> selectableWithoutDefinition = AnimagusForms.IDS.stream()
                .filter(id -> AnimagusFormBinding.resolve(id).isEmpty())
                .toList();
        List<String> definitionsNotSelectable = AnimagusFormRegistry.ids().stream()
                .map(AnimagusFormBinding::toStoredId)
                .filter(stored -> !AnimagusForms.isAnimagusForm(stored))
                .sorted()
                .toList();

        if (!selectableWithoutDefinition.isEmpty()) {
            LOGGER.warn("[Animagus] {} selectable form(s) have no datapack definition and will get no "
                            + "capabilities, attributes or senses: {}",
                    selectableWithoutDefinition.size(), selectableWithoutDefinition);
        }
        if (!definitionsNotSelectable.isEmpty()) {
            LOGGER.warn("[Animagus] {} loaded definition(s) are not selectable by any player and are "
                            + "unreachable in game: {}",
                    definitionsNotSelectable.size(), definitionsNotSelectable);
        }
    }
}
