package at.koopro.wizardsandbeasts.world.tree;

import at.koopro.wizardsandbeasts.world.ModConfiguredFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.HashMap;
import java.util.Map;

public final class TreeDebugUtil {

    public static final String[] TREE_TYPES = {"elder", "yew", "holly", "rowan"};

    public static final Map<String, ResourceKey<ConfiguredFeature<?, ?>>> TREE_KEYS = Map.of(
            "elder", ModConfiguredFeatures.ELDER_TREE_KEY,
            "yew", ModConfiguredFeatures.YEW_TREE_KEY,
            "holly", ModConfiguredFeatures.HOLLY_TREE_KEY,
            "rowan", ModConfiguredFeatures.ROWAN_TREE_KEY
    );

    public static final int SCAN_RADIUS = 15;
    public static final int SCAN_HEIGHT = 25;

    public static Map<BlockPos, BlockState> snapshotArea(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -1; y <= SCAN_HEIGHT; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    snapshot.put(p.immutable(), level.getBlockState(p));
                }
            }
        }
        return snapshot;
    }

    private TreeDebugUtil() {
    }
}
