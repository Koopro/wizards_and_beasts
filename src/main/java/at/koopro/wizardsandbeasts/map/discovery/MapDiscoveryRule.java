package at.koopro.wizardsandbeasts.map.discovery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * How the Marauder's Map learns that a place exists.
 *
 * <p>Datapack-driven from {@code data/<ns>/map_discovery/*.json}, on the same sealed-interface +
 * dispatch-codec pattern as {@code SkillNodeEffect} and {@code CreatureAbility}. Two variants, and
 * they exist because this mod's important places arrive by two completely different routes:
 *
 * <ul>
 *   <li>{@link FromStructure} — worldgen put it there. Azkaban and the Chamber of Secrets are
 *       structures, as are villages and fortresses.</li>
 *   <li>{@link FromBlocks} — a <em>player</em> built it. Hogwarts, Hogsmeade, Diagon Alley,
 *       Gringotts and the Ministry are not generated in this mod; they are block palettes
 *       ({@code HogwartsBlocks} and friends) that players build with. A castle only exists once
 *       someone has laid enough of its stone, so the map finds it by counting.</li>
 * </ul>
 *
 * <p>Rules deliberately carry no icon, colour or text beyond a translation key: what a marker
 * <em>looks</em> like is a client resource, so a resource pack can restyle the map without a
 * datapack and a server never ships pixels.
 */
public sealed interface MapDiscoveryRule permits MapDiscoveryRule.FromStructure, MapDiscoveryRule.FromBlocks {

    Codec<MapDiscoveryRule> CODEC = Type.CODEC.dispatch(MapDiscoveryRule::type, Type::codec);

    Type type();

    /** Marker type id planted when this rule fires. */
    Identifier marker();

    /** Translation key for the marker's name on the parchment. */
    String label();

    enum Type implements StringRepresentable {
        STRUCTURE("structure", FromStructure.CODEC),
        BLOCKS("blocks", FromBlocks.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends MapDiscoveryRule> codec;

        Type(String serializedName, MapCodec<? extends MapDiscoveryRule> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends MapDiscoveryRule> codec() {
            return codec;
        }
    }

    /**
     * When a generated structure counts as found.
     *
     * <p>{@link #AUTO} is the default and the one that keeps the map honest without anybody having
     * to author a flag per structure: a structure whose highest piece is at or above sea level is
     * visible country and is marked when the holder walks over it, and anything that tops out below
     * sea level is a secret and is marked only once the holder has physically been inside it. That
     * single rule puts Azkaban and villages on the map, and keeps strongholds, mineshafts and the
     * Chamber of Secrets off it until they are earned.
     */
    enum Reveal implements StringRepresentable {
        /** Decide from the structure's own bounding box against sea level. */
        AUTO("auto"),
        /** Always marked when the holder passes over it. */
        SURFACE("surface"),
        /** Marked only once the holder has stood inside its bounding box. */
        ENTERED("entered");

        public static final Codec<Reveal> CODEC = StringRepresentable.fromValues(Reveal::values);

        private final String serializedName;

        Reveal(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * Marks a worldgen structure.
     *
     * <p>Either a single structure id or a structure tag, never both — a tag lets one rule cover
     * every village variant, and an id lets one rule name a specific fortress.
     */
    record FromStructure(
            java.util.Optional<ResourceKey<Structure>> structure,
            java.util.Optional<TagKey<Structure>> structureTag,
            Identifier marker,
            String label,
            Reveal reveal
    ) implements MapDiscoveryRule {

        public static final MapCodec<FromStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ResourceKey.codec(Registries.STRUCTURE).optionalFieldOf("structure")
                        .forGetter(FromStructure::structure),
                TagKey.codec(Registries.STRUCTURE).optionalFieldOf("structure_tag")
                        .forGetter(FromStructure::structureTag),
                Identifier.CODEC.fieldOf("marker").forGetter(FromStructure::marker),
                Codec.STRING.fieldOf("label").forGetter(FromStructure::label),
                Reveal.CODEC.optionalFieldOf("reveal", Reveal.AUTO).forGetter(FromStructure::reveal)
        ).apply(instance, FromStructure::new));

        @Override
        public Type type() {
            return Type.STRUCTURE;
        }
    }

    /**
     * Marks a place a player built, by counting blocks from a tag inside one chunk.
     *
     * <p>{@code threshold} is per chunk, not per structure, which is what keeps the scan cheap: a
     * chunk either carries enough of the palette to be part of the castle or it does not, and
     * neighbouring chunks that also qualify merge into the same marker via
     * {@code MapAtlas.MERGE_TOLERANCE}. Set it high enough that a decorative wall does not read as
     * Hogwarts and low enough that one corner tower does.
     */
    record FromBlocks(
            TagKey<Block> blocks,
            int threshold,
            Identifier marker,
            String label
    ) implements MapDiscoveryRule {

        /** Below this a rule would fire on a doorstep; guarded so a typo cannot litter the map. */
        public static final int MIN_THRESHOLD = 16;

        public static final MapCodec<FromBlocks> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TagKey.codec(Registries.BLOCK).fieldOf("blocks").forGetter(FromBlocks::blocks),
                Codec.intRange(MIN_THRESHOLD, 4096).optionalFieldOf("threshold", 64)
                        .forGetter(FromBlocks::threshold),
                Identifier.CODEC.fieldOf("marker").forGetter(FromBlocks::marker),
                Codec.STRING.fieldOf("label").forGetter(FromBlocks::label)
        ).apply(instance, FromBlocks::new));

        /** Guards against a pack pointing a rule at a tag that would match most of the world. */
        public boolean isSane() {
            return !blocks.equals(BlockTags.MINEABLE_WITH_PICKAXE) && threshold >= MIN_THRESHOLD;
        }

        @Override
        public Type type() {
            return Type.BLOCKS;
        }
    }
}
