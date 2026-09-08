package at.koopro.wizardsandbeasts.client.brew;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.registry.ModBlocks;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * One rig, three metals.
 *
 * <p>The three cauldrons share a single {@code BlockEntityType} — which is deliberate, and documented
 * where it is registered: one type means a recipe or a tooltip can ask "is this a cauldron" without
 * enumerating metals. That leaves the renderer with one geometry and three sheets to choose between,
 * so the choice moves here.
 *
 * <p>It reads the block straight off {@link BlockEntityRenderState#blockState}, a public field vanilla
 * fills for every block entity render state. No data ticket, no extra sync: the block a cauldron is
 * has been known to the client since the chunk loaded. This mirrors {@code GoblinRenderer}, which
 * picks its texture from a synced role rather than existing three times over.
 */
public class CauldronGeoModel extends DefaultedBlockGeoModel<CauldronBlockEntity> {

    private static final Identifier PEWTER = sheet("pewter");
    private static final Identifier BRASS = sheet("brass");
    private static final Identifier COPPER = sheet("wizarding_copper");

    public CauldronGeoModel() {
        super(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wizarding_cauldron"));
    }

    private static Identifier sheet(String metal) {
        return Identifier.fromNamespaceAndPath(
                WizardsAndBeastsMod.MODID, "textures/block/wizarding_cauldron_" + metal + ".png");
    }

    /**
     * Pewter is the fallback, not an arbitrary default: it is the student cauldron, the one a player
     * meets first, so an unrecognised block renders as the humblest pot rather than as brass.
     */
    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        if (!(renderState instanceof BlockEntityRenderState blockState)) {
            return PEWTER;
        }
        Block block = blockState.blockState.getBlock();
        if (block == ModBlocks.BRASS_CAULDRON.get()) {
            return BRASS;
        }
        if (block == ModBlocks.WIZARDING_COPPER_CAULDRON.get()) {
            return COPPER;
        }
        return PEWTER;
    }
}
