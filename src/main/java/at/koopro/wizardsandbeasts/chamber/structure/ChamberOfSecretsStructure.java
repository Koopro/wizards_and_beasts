package at.koopro.wizardsandbeasts.chamber.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * The Chamber of Secrets: an ordinary vanilla jigsaw structure whose <em>generation</em> is gated on
 * {@link Module#CHAMBER_OF_SECRETS}. Registration is never gated — the structure type and the structure
 * itself always register; this only decides whether a chunk is allowed to place one.
 *
 * <p><b>Why composition and not a subclass.</b> {@link JigsawStructure} is {@code final}, so the gate
 * cannot be an overridden {@code findGenerationPoint} calling {@code super}. It holds one instead, and
 * the codec is {@link JigsawStructure#CODEC} mapped onto this wrapper — so every jigsaw field (start
 * pool, size, start height, expansion hack, heightmap projection, max distance, pool aliases, dimension
 * padding, liquid settings) is read by vanilla's own codec, including its range validation. There is no
 * mirrored field list here to drift out of step with a future Minecraft version, and the structure JSON
 * is byte-identical to the {@code minecraft:jigsaw} one it replaces apart from its {@code type}.
 *
 * <p>The settings the wrapper reports to the world (biomes, step, terrain adaptation, spawn overrides)
 * are the delegate's own, so NeoForge structure modifiers and biome placement see exactly what a plain
 * jigsaw structure would.
 *
 * <p>Gating only ever suppresses <em>new</em> placement. A chamber already written into a world's chunks
 * stays there and keeps working; nothing here reads or edits generated terrain.
 *
 * @see at.koopro.wizardsandbeasts.azkaban.structure.AzkabanStructure the same gate on the other structure
 */
@NullMarked
public final class ChamberOfSecretsStructure extends Structure {

    public static final MapCodec<ChamberOfSecretsStructure> CODEC =
            JigsawStructure.CODEC.xmap(ChamberOfSecretsStructure::new, structure -> structure.jigsaw);

    private final JigsawStructure jigsaw;

    public ChamberOfSecretsStructure(JigsawStructure jigsaw) {
        // The original (pre-structure-modifier) settings, which is what the delegate was built with and
        // what Structure's own settings codec round-trips.
        super(jigsaw.modifiableStructureInfo().getOriginalStructureInfo().structureSettings());
        this.jigsaw = jigsaw;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Registration is always present; the CHAMBER_OF_SECRETS module gates *generation* only.
        // Not accessible (DISABLED or COMING_SOON) means no placement at all — no partial piece, no
        // air pocket, no marker; the chunk generates as if the structure were not in the set.
        if (!ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS)) {
            return Optional.empty();
        }
        return jigsaw.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type() {
        return ChamberOfSecretsStructures.CHAMBER_OF_SECRETS.get();
    }
}
