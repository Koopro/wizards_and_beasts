package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.skill.SkillNodeEffect;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.build.Archetype;
import at.koopro.wizardsandbeasts.skill.build.BuildPathway;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-driven Vocation — a declared identity tied to one wizard {@link SkillTreeId} home region.
 * Since the web rework Phase 3 it gates nothing: the mastery-band / opposition enforcement is gone
 * ({@code foundationMaxTier} and {@code oppositions} removed from the schema); {@link #tree()} is the
 * home-region hook the future in-region-bonus prompt consumes.
 *
 * <p>Loaded from {@code data/wizards_and_beasts/vocations/<id>.json} by {@link VocationLoader}, mirroring
 * {@code BestiaryEntryLoader}. Translatable copy is stored as keys (see {@code BestiaryEntry}); call the
 * {@link #displayName()}/{@link #description()}/{@link #pillar()} getters for the resolved {@link Component}.
 *
 * @param id                identity, e.g. {@code wizards_and_beasts:duelist}
 * @param tree              home region; a node belongs iff {@code node.getTree() == tree}
 * @param displayNameKey    translation key for the display name
 * @param descriptionKey    translation key for the description
 * @param pillarKey         translation key for the one-line "what this owns" copy
 * @param capstoneNodeId    the single capstone node id (path = the node's String id), or empty. Read again since
 *                          the 2026-10-14 builds pass: with {@link #pathway} it is the build's capstone, gated
 *                          by {@code BuildRules}.
 * @param commitmentEffects declared stat profile — only {@link SkillNodeEffect.AttributeBoost} entries are
 *                          actually applied by {@link VocationEffectApplicator}; author them with a
 *                          Vocation-scoped {@code modifier_id} ({@code vocation/<id>/<attr>})
 * @param grantedAbilities  descriptive ability-flag ids granted on declaration. NOT a {@link SkillNodeEffect}
 *                          variant; consumed live by {@link VocationAbilityHooks} and the skill event
 *                          handlers (query via {@link VocationHelper#hasGrantedAbility}).
 * @param pathway           the build this vocation names: early / mid / advanced nodes through the existing web.
 *                          With {@link #capstoneNodeId} it makes the vocation an {@link Archetype}; without either
 *                          the vocation stays a plain declaration.
 */
@NullMarked
public record VocationDefinition(
        Identifier id,
        SkillTreeId tree,
        String displayNameKey,
        String descriptionKey,
        String pillarKey,
        Optional<Identifier> capstoneNodeId,
        List<SkillNodeEffect> commitmentEffects,
        List<String> grantedAbilities,
        Optional<BuildPathway> pathway,
        Optional<OwlGate> owlGate) {

    /**
     * The O.W.L. a wizard must hold to commit to this vocation (systems audit D2): one subject per vocation, the grade
     * at or above {@code grade}. Canon: Aurors need DADA among their O.W.L.s and N.E.W.T.s (OotP ch.29, McGonagall's
     * careers advice); the rest follow the subject each trade is built on.
     */
    public record OwlGate(at.koopro.wizardsandbeasts.owl.OWLSubject subject, at.koopro.wizardsandbeasts.owl.OWLGrade grade) {
        private static final Codec<at.koopro.wizardsandbeasts.owl.OWLGrade> GRADE = Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(at.koopro.wizardsandbeasts.owl.OWLGrade.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown O.W.L. grade: " + s);
            }
        }, g -> g.name());
        public static final Codec<OwlGate> CODEC = RecordCodecBuilder.create(i -> i.group(
                at.koopro.wizardsandbeasts.owl.OWLSubject.CODEC.fieldOf("subject").forGetter(OwlGate::subject),
                GRADE.optionalFieldOf("grade", at.koopro.wizardsandbeasts.owl.OWLGrade.A).forGetter(OwlGate::grade)
        ).apply(i, OwlGate::new));

        /** Whether {@code grades} (a wizard's best O.W.L.s) meet this gate. */
        public boolean metBy(java.util.Map<at.koopro.wizardsandbeasts.owl.OWLSubject, at.koopro.wizardsandbeasts.owl.OWLGrade> grades) {
            at.koopro.wizardsandbeasts.owl.OWLGrade held = grades.get(subject);
            return held != null && held.value >= grade.value;
        }
    }

    public static final Codec<SkillTreeId> TREE_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                SkillTreeId tree = SkillTreeId.byId(s);
                return tree != null
                        ? DataResult.success(tree)
                        : DataResult.error(() -> "Unknown skill tree: " + s);
            },
            SkillTreeId::getId);

    public static final Codec<VocationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(VocationDefinition::id),
            TREE_CODEC.fieldOf("tree").forGetter(VocationDefinition::tree),
            Codec.STRING.fieldOf("displayName").forGetter(VocationDefinition::displayNameKey),
            Codec.STRING.fieldOf("description").forGetter(VocationDefinition::descriptionKey),
            Codec.STRING.fieldOf("pillar").forGetter(VocationDefinition::pillarKey),
            Identifier.CODEC.optionalFieldOf("capstoneNodeId").forGetter(VocationDefinition::capstoneNodeId),
            // Lazy: SkillNodeEffect.CODEC pulls the attribute registry on class-init. Deferring it keeps
            // VocationDefinition loadable (e.g. in unit tests) without a full Minecraft bootstrap.
            Codec.lazyInitialized(() -> SkillNodeEffect.CODEC.listOf())
                    .optionalFieldOf("commitmentEffects", List.of())
                    .forGetter(VocationDefinition::commitmentEffects),
            Codec.STRING.listOf().optionalFieldOf("grantedAbilities", List.of())
                    .forGetter(VocationDefinition::grantedAbilities),
            BuildPathway.CODEC.optionalFieldOf("pathway").forGetter(VocationDefinition::pathway),
            OwlGate.CODEC.optionalFieldOf("owlGate").forGetter(VocationDefinition::owlGate)
    ).apply(instance, VocationDefinition::new));

    public Component displayName() { return Component.translatable(displayNameKey); }
    public Component description() { return Component.translatable(descriptionKey); }
    public Component pillar() { return Component.translatable(pillarKey); }

    /**
     * This vocation as a build, when it authors both a pathway and a capstone. The capstone id's path is the node's
     * plain skill id ({@code wizards_and_beasts:duelling_riposte} → {@code duelling_riposte}).
     */
    public Optional<Archetype> archetype() {
        if (pathway.isEmpty() || capstoneNodeId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Archetype(id, displayNameKey, pathway.get(), capstoneNodeId.get().getPath()));
    }
}
