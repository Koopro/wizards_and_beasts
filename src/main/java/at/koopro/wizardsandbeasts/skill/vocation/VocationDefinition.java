package at.koopro.wizardsandbeasts.skill.vocation;

import at.koopro.wizardsandbeasts.skill.SkillNodeEffect;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * Datapack-driven Vocation specialization. A Vocation reframes one existing wizard {@link SkillTreeId} as a
 * commitment with opportunity cost: Foundation tiers stay open to all, Mastery tiers gate on commitment.
 *
 * <p>Loaded from {@code data/wizards_and_beasts/vocations/<id>.json} by {@link VocationLoader}, mirroring
 * {@code BestiaryEntryLoader}. Translatable copy is stored as keys (see {@code BestiaryEntry}); call the
 * {@link #displayName()}/{@link #description()}/{@link #pillar()} getters for the resolved {@link Component}.
 *
 * @param id                identity, e.g. {@code wizards_and_beasts:duelist}
 * @param tree              existing tree this Vocation owns; a node belongs iff {@code node.getTree() == tree}
 * @param displayNameKey    translation key for the display name
 * @param descriptionKey    translation key for the description
 * @param pillarKey         translation key for the one-line "what this owns" copy
 * @param foundationMaxTier tiers {@code <=} this are Foundation (open to all); above are Mastery. Default 1.
 * @param capstoneNodeId    the single capstone node id (path = the node's String id), or empty
 * @param oppositions       Vocation ids that hard-lock this one (enforced symmetrically by the registry)
 * @param commitmentEffects primary stat profile — only {@link SkillNodeEffect.AttributeBoost} entries are
 *                          actually applied by {@link VocationEffectApplicator}; author them with a
 *                          Vocation-scoped {@code modifier_id} ({@code vocation/<id>/<attr>})
 * @param grantedAbilities  descriptive ability-flag ids granted on commit. NOT a {@link SkillNodeEffect}
 *                          variant; consumed live by {@link VocationAbilityHooks} and the skill event
 *                          handlers (query via {@link VocationHelper#hasGrantedAbility}).
 */
@NullMarked
public record VocationDefinition(
        Identifier id,
        SkillTreeId tree,
        String displayNameKey,
        String descriptionKey,
        String pillarKey,
        int foundationMaxTier,
        Optional<Identifier> capstoneNodeId,
        List<Identifier> oppositions,
        List<SkillNodeEffect> commitmentEffects,
        List<String> grantedAbilities) {

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
            Codec.INT.optionalFieldOf("foundationMaxTier", 1).forGetter(VocationDefinition::foundationMaxTier),
            Identifier.CODEC.optionalFieldOf("capstoneNodeId").forGetter(VocationDefinition::capstoneNodeId),
            Identifier.CODEC.listOf().optionalFieldOf("oppositions", List.of()).forGetter(VocationDefinition::oppositions),
            // Lazy: SkillNodeEffect.CODEC pulls the attribute registry on class-init. Deferring it keeps
            // VocationDefinition loadable (e.g. in unit tests) without a full Minecraft bootstrap.
            Codec.lazyInitialized(() -> SkillNodeEffect.CODEC.listOf())
                    .optionalFieldOf("commitmentEffects", List.of())
                    .forGetter(VocationDefinition::commitmentEffects),
            Codec.STRING.listOf().optionalFieldOf("grantedAbilities", List.of())
                    .forGetter(VocationDefinition::grantedAbilities)
    ).apply(instance, VocationDefinition::new));

    public Component displayName() { return Component.translatable(displayNameKey); }
    public Component description() { return Component.translatable(descriptionKey); }
    public Component pillar() { return Component.translatable(pillarKey); }
}
