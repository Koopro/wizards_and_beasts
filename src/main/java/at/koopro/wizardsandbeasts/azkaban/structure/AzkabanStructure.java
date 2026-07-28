package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Data-driven Azkaban structure: a procedural bare-rock crag rising from the sea
 * floor with a level platform auto-sized to a supplied NBT template, onto which
 * the template (the fortress geometry, hand-built by the author) is dropped.
 *
 * <p>All build parameters are exposed on the {@link #CODEC} so the crag and the
 * template are tunable from the structure JSON without recompiling:
 * <ul>
 *   <li>{@code template} — the NBT identifier to place on the platform
 *   <li>{@code crag_height} — rock blocks above sea level (default 10)
 *   <li>{@code platform_margin} — extra flat rock ring around the template
 *       footprint (default 3)
 *   <li>{@code palette} — crag body / lower / accent blocks
 * </ul>
 *
 * <p>Generation degrades gracefully: if the template NBT is absent (the author
 * has not yet exported it) the structure is skipped with a single logged
 * warning rather than crashing.
 */
public final class AzkabanStructure extends Structure {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean MISSING_TEMPLATE_WARNED = new AtomicBoolean(false);

    /** Crag block palette. Defaults to stone body / deepslate lower / gravel accents. */
    public record Palette(@NonNull Block body, @NonNull Block lower, @NonNull Block accent) {
        public static final Palette DEFAULT = new Palette(Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRAVEL);

        public static final Codec<Palette> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("body", Blocks.STONE).forGetter(Palette::body),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("lower", Blocks.DEEPSLATE).forGetter(Palette::lower),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("accent", Blocks.GRAVEL).forGetter(Palette::accent)
        ).apply(instance, Palette::new));
    }

    public static final MapCodec<AzkabanStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            Identifier.CODEC.fieldOf("template").forGetter(s -> s.template),
            Codec.INT.optionalFieldOf("crag_height", 10).forGetter(s -> s.cragHeight),
            Codec.INT.optionalFieldOf("platform_margin", 3).forGetter(s -> s.platformMargin),
            Palette.CODEC.optionalFieldOf("palette", Palette.DEFAULT).forGetter(s -> s.palette)
    ).apply(instance, AzkabanStructure::new));

    private final Identifier template;
    private final int cragHeight;
    private final int platformMargin;
    private final Palette palette;

    public AzkabanStructure(@NonNull StructureSettings settings,
                            @NonNull Identifier template,
                            int cragHeight,
                            int platformMargin,
                            @NonNull Palette palette) {
        super(settings);
        this.template = template;
        this.cragHeight = cragHeight;
        this.platformMargin = platformMargin;
        this.palette = palette;
    }

    @Override
    public @NonNull StructureType<?> type() {
        return AzkabanStructures.AZKABAN_FORTRESS.get();
    }

    @Override
    protected @NonNull Optional<GenerationStub> findGenerationPoint(@NonNull GenerationContext context) {
        // Registration is always present; the AZKABAN module gates *generation* only.
        // ModuleDefaults ships AZKABAN as DISABLED, deliberately: the fortress NBT is still a 225-byte
        // placeholder, so a generated Azkaban would be an empty box. An operator can switch it on.
        if (!ModuleManager.isEnabled(Module.AZKABAN)) {
            return Optional.empty();
        }

        StructureTemplateManager templates = context.structureTemplateManager();
        Optional<StructureTemplate> loaded = templates.get(template);
        if (loaded.isEmpty()) {
            if (MISSING_TEMPLATE_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("[WizardsAndBeasts] Azkaban template '{}' is missing — skipping structure generation. "
                        + "Drop the NBT at data/<ns>/structure/<path>.nbt to enable it.", template);
            }
            return Optional.empty();
        }

        Vec3i size = loaded.get().getSize();
        int seaLevel = context.chunkGenerator().getSeaLevel();
        int platformTopY = seaLevel + cragHeight;

        ChunkPos chunk = context.chunkPos();
        int centerX = chunk.getMiddleBlockX();
        int centerZ = chunk.getMiddleBlockZ();

        // Flat platform = template footprint + margin ring on every side.
        int halfPlatformX = (size.getX() + 1) / 2 + platformMargin;
        int halfPlatformZ = (size.getZ() + 1) / 2 + platformMargin;

        BlockPos center = new BlockPos(centerX, platformTopY, centerZ);
        // Template's lowest layer sits one block above the solid platform top.
        BlockPos templatePos = new BlockPos(
                centerX - size.getX() / 2,
                platformTopY + 1,
                centerZ - size.getZ() / 2);

        Identifier templateId = template;
        int cragH = cragHeight;
        Palette pal = palette;

        return Optional.of(new GenerationStub(center, builder -> {
            builder.addPiece(new AzkabanCragPiece(
                    center, halfPlatformX, halfPlatformZ, cragH, seaLevel, pal));
            builder.addPiece(new AzkabanTemplatePiece(
                    context.structureTemplateManager(), templateId, templatePos));
        }));
    }
}
