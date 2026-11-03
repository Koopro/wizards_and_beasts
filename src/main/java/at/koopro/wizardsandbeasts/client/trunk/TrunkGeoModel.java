package at.koopro.wizardsandbeasts.client.trunk;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlock;
import at.koopro.wizardsandbeasts.block.trunk.TrunkBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Five trunks, two rigs (from {@code tools/trunk_model.py}). They share one {@code BlockEntityType}, so -- as
 * {@code CauldronGeoModel} does for its three metals -- the block decides here: Newt's Case is the suitcase rig, every
 * other trunk the chest rig, and each block wears its own sheet, {@code textures/block/<block id>_rig.png}.
 */
public class TrunkGeoModel extends DefaultedBlockGeoModel<TrunkBlockEntity> {

    private static final Identifier TRUNK = id("trunk");
    private static final Identifier CASE = id("newts_case");
    private static final Identifier FALLBACK_SHEET = sheet("enchanted_trunk");

    public TrunkGeoModel() {
        super(id("trunk"));
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name);
    }

    private static Identifier sheet(String block) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/block/" + block + "_rig.png");
    }

    private static boolean isCase(Block block) {
        return block instanceof TrunkBlock trunk && trunk.isCase();
    }

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        boolean asCase = renderState instanceof BlockEntityRenderState state && isCase(state.blockState.getBlock());
        return buildFormattedModelPath(asCase ? CASE : TRUNK);
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        if (!(renderState instanceof BlockEntityRenderState state)) {
            return FALLBACK_SHEET;
        }
        return sheet(BuiltInRegistries.BLOCK.getKey(state.blockState.getBlock()).getPath());
    }

    @Override
    public Identifier getAnimationResource(TrunkBlockEntity animatable) {
        return buildFormattedAnimationPath(isCase(animatable.getBlockState().getBlock()) ? CASE : TRUNK);
    }
}
