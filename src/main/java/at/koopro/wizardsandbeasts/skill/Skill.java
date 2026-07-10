package at.koopro.wizardsandbeasts.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An individual node in a skill web. Immutable after construction.
 * Built via {@link #builder(String, String)} or parsed from datapack JSON via {@link #CODEC}.
 *
 * <p>Web model (Phase 2): nodes live at datapack-defined {@code x}/{@code y} layout coordinates
 * (arbitrary per-web units, y-down) and connect via logically bidirectional {@code edges}
 * (declared on either endpoint; symmetrized at load into {@link SkillTrees}). A node's first level
 * is allocatable iff it is a {@code root} node or any edge-neighbor is at level ≥ 1 — see
 * {@link SkillSystemAPI#evaluateUnlock}.
 */
public final class Skill {

    /** Visual weight of a node on the canvas. Gameplay-inert in Phase 2. */
    public enum Size implements StringRepresentable {
        SMALL("small"),
        NOTABLE("notable"),
        KEYSTONE("keystone");

        public static final Codec<Size> CODEC = StringRepresentable.fromValues(Size::values);

        private final String serializedName;

        Size(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * Datapack codec. {@code nodeEffects} is lazily initialized so parsing/encoding nodes without
     * explicit node effects (all current content) never touches {@link SkillNodeEffect}'s
     * registry-backed codecs.
     */
    public static final Codec<Skill> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Skill::getId),
            Codec.STRING.fieldOf("displayName").forGetter(Skill::getDisplayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Skill::getDescription),
            SkillTreeId.CODEC.fieldOf("tree").forGetter(Skill::getTree),
            Codec.INT.optionalFieldOf("maxLevel", 1).forGetter(Skill::getMaxLevel),
            Codec.INT.optionalFieldOf("pointCost", 1).forGetter(Skill::getPointCost),
            SkillEffect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Skill::getEffects),
            Codec.lazyInitialized(() -> SkillNodeEffect.CODEC.listOf())
                    .optionalFieldOf("nodeEffects", List.of()).forGetter(Skill::getExplicitNodeEffects),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Skill::getX),
            Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(Skill::getY),
            Codec.STRING.listOf().optionalFieldOf("edges", List.of()).forGetter(Skill::getEdges),
            Size.CODEC.optionalFieldOf("size", Size.NOTABLE).forGetter(Skill::getSize),
            Codec.BOOL.optionalFieldOf("root", false).forGetter(Skill::isRoot)
    ).apply(instance, Skill::fromCodec));

    public static final StreamCodec<ByteBuf, Skill> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    private final String id;
    private final String displayName;
    private final String description;
    private final SkillTreeId tree;
    private final int maxLevel;
    private final int pointCost;
    private final List<SkillEffect> effects;
    /** Node effects declared explicitly (builder/JSON); empty for every current node. */
    private final List<SkillNodeEffect> explicitNodeEffects;
    /** Derived from {@link #effects} on first use when no explicit node effects exist (requires MC bootstrap). */
    private @Nullable List<SkillNodeEffect> derivedNodeEffects;
    /** Web layout coordinates: arbitrary per-web units, y-down. Placeholder values until the layout pass. */
    private final double x;
    private final double y;
    /** Edge-neighbor node ids. Logically bidirectional; either endpoint may declare the edge. */
    private final List<String> edges;
    private final Size size;
    /** Web entry point: allocatable without any allocated neighbor. */
    private final boolean root;

    private Skill(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.tree = builder.tree;
        this.maxLevel = builder.maxLevel;
        this.pointCost = builder.pointCost;
        this.effects = Collections.unmodifiableList(new ArrayList<>(builder.effects));
        this.explicitNodeEffects = Collections.unmodifiableList(new ArrayList<>(builder.nodeEffects));
        this.x = builder.x;
        this.y = builder.y;
        this.edges = Collections.unmodifiableList(new ArrayList<>(builder.edges));
        this.size = builder.size;
        this.root = builder.root;
    }

    private static Skill fromCodec(String id, String displayName, String description, SkillTreeId tree,
                                   int maxLevel, int pointCost, List<SkillEffect> effects,
                                   List<SkillNodeEffect> nodeEffects,
                                   double x, double y, List<String> edges, Size size, boolean root) {
        Builder builder = builder(id, displayName)
                .description(description)
                .tree(tree)
                .maxLevel(maxLevel)
                .cost(pointCost)
                .position(x, y)
                .size(size)
                .root(root);
        effects.forEach(builder::effect);
        nodeEffects.forEach(builder::nodeEffect);
        edges.forEach(builder::edge);
        return builder.build();
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public SkillTreeId getTree() { return tree; }
    public int getMaxLevel() { return maxLevel; }
    public int getPointCost() { return pointCost; }
    public List<SkillEffect> getEffects() { return effects; }

    /** Explicitly declared node effects only — never the derived ones. This is what serializes. */
    public List<SkillNodeEffect> getExplicitNodeEffects() { return explicitNodeEffects; }

    public List<SkillNodeEffect> getNodeEffects() {
        if (!explicitNodeEffects.isEmpty()) {
            return explicitNodeEffects;
        }
        if (derivedNodeEffects == null) {
            derivedNodeEffects = deriveNodeEffects();
        }
        return derivedNodeEffects;
    }

    private List<SkillNodeEffect> deriveNodeEffects() {
        List<SkillNodeEffect> derived = new ArrayList<>();
        for (SkillEffect effect : effects) {
            if (effect instanceof SkillEffect.CategoryCooldownReduction reduction) {
                derived.add(new SkillNodeEffect.SpellCooldownMultiplier(
                        reduction.category(),
                        Math.max(0.1f, 1.0f - reduction.reductionPerLevel())));
            } else if (effect instanceof SkillEffect.CategoryDamageBonus bonus) {
                derived.add(new SkillNodeEffect.SpellDamageMultiplier(
                        bonus.category(),
                        1.0f + bonus.bonusPerLevel()));
            } else if (effect instanceof SkillEffect.PassiveAttribute passive) {
                var attribute = switch (passive.attributeId()) {
                    case "max_health" -> net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
                    case "movement_speed" -> net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
                    case "armor" -> net.minecraft.world.entity.ai.attributes.Attributes.ARMOR;
                    default -> null;
                };
                if (attribute != null) {
                    derived.add(new SkillNodeEffect.AttributeBoost(
                            attribute,
                            passive.amountPerLevel(),
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    "wizards_and_beasts",
                                    "skill/" + tree.getId() + "/" + id + "/" + passive.attributeId())));
                }
            }
        }
        return Collections.unmodifiableList(derived);
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public List<String> getEdges() { return edges; }
    public Size getSize() { return size; }
    public boolean isRoot() { return root; }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private String description = "";
        private SkillTreeId tree;
        private int maxLevel = 1;
        private int pointCost = 1;
        private final List<SkillEffect> effects = new ArrayList<>();
        private final List<SkillNodeEffect> nodeEffects = new ArrayList<>();
        private double x;
        private double y;
        private final List<String> edges = new ArrayList<>();
        private Size size = Size.NOTABLE;
        private boolean root;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder tree(SkillTreeId tree) {
            this.tree = tree;
            return this;
        }

        public Builder maxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }

        public Builder cost(int pointCost) {
            this.pointCost = pointCost;
            return this;
        }

        public Builder effect(SkillEffect effect) {
            this.effects.add(effect);
            return this;
        }

        public Builder nodeEffect(SkillNodeEffect effect) {
            this.nodeEffects.add(effect);
            return this;
        }

        public Builder position(double x, double y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder edge(String neighborId) {
            this.edges.add(neighborId);
            return this;
        }

        public Builder size(Size size) {
            this.size = size;
            return this;
        }

        public Builder root(boolean root) {
            this.root = root;
            return this;
        }

        public Skill build() {
            if (tree == null) throw new IllegalStateException("Skill '" + id + "' must have a tree");
            return new Skill(this);
        }
    }
}
