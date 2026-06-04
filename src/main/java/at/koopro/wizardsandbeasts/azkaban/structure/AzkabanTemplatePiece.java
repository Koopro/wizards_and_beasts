package at.koopro.wizardsandbeasts.azkaban.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.NonNull;

/**
 * Places the Azkaban NBT template (the hand-built fortress geometry) on top of
 * the {@link AzkabanCragPiece} platform.
 *
 * <p>If the template file is missing, {@link StructureTemplateManager} resolves
 * an empty template and nothing is placed — no crash. (The owning structure also
 * skips generation entirely when the template is absent; this is the belt-and-
 * braces fallback.)
 */
public final class AzkabanTemplatePiece extends TemplateStructurePiece {

    public AzkabanTemplatePiece(@NonNull StructureTemplateManager manager,
                                @NonNull Identifier template,
                                @NonNull BlockPos position) {
        super(AzkabanStructures.AZKABAN_TEMPLATE_PIECE.get(), 0, manager, template,
                template.toString(), makeSettings(), position);
    }

    public AzkabanTemplatePiece(@NonNull StructurePieceSerializationContext ctx,
                                @NonNull CompoundTag tag) {
        super(AzkabanStructures.AZKABAN_TEMPLATE_PIECE.get(), tag, ctx.structureTemplateManager(),
                id -> makeSettings());
    }

    private static StructurePlaceSettings makeSettings() {
        return new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);
    }

    @Override
    protected void handleDataMarker(@NonNull String name,
                                    @NonNull BlockPos pos,
                                    @NonNull ServerLevelAccessor level,
                                    @NonNull RandomSource random,
                                    @NonNull BoundingBox box) {
        // Placeholder template has no data markers; nothing to handle.
    }
}
