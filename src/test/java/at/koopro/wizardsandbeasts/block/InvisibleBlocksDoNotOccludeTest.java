package at.koopro.wizardsandbeasts.block;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block that draws nothing must not occlude anything.
 *
 * <p>{@link RenderShape#INVISIBLE} is how every rig-drawn block in the mod works: the block itself
 * renders nothing and a GeckoLib block-entity renderer draws it instead. But occlusion is a separate
 * property from rendering. An occluding block tells the chunk mesher to cull the touching faces of
 * its neighbours — so a block that occludes and draws nothing leaves a hole, and you see straight
 * through the world where it stands.
 *
 * <p>The wandmaker's bench shipped exactly that. It was converted to a rig without
 * {@code .noOcclusion()} on its properties and, having no {@code getShape} override either, it
 * occluded as a full cube while drawing nothing at all. The tents and the cauldrons had always set
 * it; the bench was simply missed, and nothing checked.
 *
 * <p>Registry-wide rather than a list, because the point is to catch the <em>next</em> one.
 */
class InvisibleBlocksDoNotOccludeTest {

    @Test
    void everyInvisibleBlockInTheModIsAlsoNonOccluding() {
        List<String> holes = new ArrayList<>();
        int invisible = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(WizardsAndBeastsMod.MODID)) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.getRenderShape() != RenderShape.INVISIBLE) {
                    continue;
                }
                invisible++;
                if (state.canOcclude()) {
                    holes.add(BuiltInRegistries.BLOCK.getKey(block)
                            + " is INVISIBLE but still occludes — add .noOcclusion() to its properties");
                }
            }
        }

        assertTrue(invisible > 0, "no INVISIBLE blocks found; the registry probably did not load");
        assertEquals(List.of(), holes, "blocks you can see the void through");
    }
}
