package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.world.tree.TreeDebugUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DebugWandState {

    private static final Map<UUID, DebugWandState> STATES = new ConcurrentHashMap<>();

    private int treeTypeIndex;
    private BlockPos previewOrigin;
    private Map<BlockPos, BlockState> originalBlocks;
    private Map<BlockPos, BlockState> treeBlocks;

    private DebugWandState() {
    }

    public static DebugWandState get(UUID playerId) {
        return STATES.computeIfAbsent(playerId, k -> new DebugWandState());
    }

    public static void cleanup(UUID playerId, ServerLevel level) {
        DebugWandState state = STATES.remove(playerId);
        if (state != null && state.hasPreview()) {
            state.restoreOriginal(level);
        }
    }

    public String getTreeType() {
        return TreeDebugUtil.TREE_TYPES[treeTypeIndex];
    }

    public String cycleTreeType() {
        treeTypeIndex = (treeTypeIndex + 1) % TreeDebugUtil.TREE_TYPES.length;
        return TreeDebugUtil.TREE_TYPES[treeTypeIndex];
    }

    public void setTreeType(String type) {
        for (int i = 0; i < TreeDebugUtil.TREE_TYPES.length; i++) {
            if (TreeDebugUtil.TREE_TYPES[i].equals(type)) {
                treeTypeIndex = i;
                return;
            }
        }
    }

    public boolean hasPreview() {
        return previewOrigin != null;
    }

    public boolean createPreview(ServerLevel level, BlockPos origin) {
        ResourceKey<ConfiguredFeature<?, ?>> key = TreeDebugUtil.TREE_KEYS.get(getTreeType());
        if (key == null) return false;

        Registry<ConfiguredFeature<?, ?>> registry =
                level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> feature = registry.getValue(key);
        if (feature == null) return false;

        previewOrigin = origin.immutable();
        originalBlocks = TreeDebugUtil.snapshotArea(level, origin);

        boolean placed = feature.place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(), origin);
        if (!placed) {
            resetState();
            return false;
        }

        treeBlocks = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : originalBlocks.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState oldState = entry.getValue();
            BlockState newState = level.getBlockState(p);

            if (oldState != newState) {
                treeBlocks.put(p, newState);
                if (newState.is(BlockTags.LOGS)) {
                    level.setBlock(p, Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), 3);
                } else if (newState.is(BlockTags.LEAVES)) {
                    level.setBlock(p, Blocks.LIME_STAINED_GLASS.defaultBlockState(), 3);
                }
            }
        }
        return true;
    }

    public int confirm(ServerLevel level) {
        if (treeBlocks == null) return 0;

        int count = treeBlocks.size();
        for (Map.Entry<BlockPos, BlockState> entry : treeBlocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        resetState();
        return count;
    }

    public void clearPreview(ServerLevel level) {
        restoreOriginal(level);
        resetState();
    }

    private void restoreOriginal(ServerLevel level) {
        if (originalBlocks == null || treeBlocks == null) return;

        for (BlockPos p : treeBlocks.keySet()) {
            BlockState original = originalBlocks.get(p);
            if (original != null) {
                level.setBlock(p, original, 3);
            }
        }
    }

    private void resetState() {
        previewOrigin = null;
        originalBlocks = null;
        treeBlocks = null;
    }

}
