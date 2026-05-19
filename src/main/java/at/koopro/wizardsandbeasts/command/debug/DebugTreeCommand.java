package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.world.tree.TreeDebugUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.Map;

public final class DebugTreeCommand {

    private DebugTreeCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("tree")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TreeDebugUtil.TREE_KEYS.keySet(), builder))
                        .executes(ctx -> spawnTree(ctx, false))
                        .then(Commands.literal("outline")
                                .executes(ctx -> spawnTree(ctx, true))
                        )
                );
    }

    private static int spawnTree(CommandContext<CommandSourceStack> ctx, boolean outline) {
        String type = StringArgumentType.getString(ctx, "type");
        ResourceKey<ConfiguredFeature<?, ?>> key = TreeDebugUtil.TREE_KEYS.get(type);

        if (key == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown tree type: " + type + ". Valid: " + String.join(", ", TreeDebugUtil.TREE_KEYS.keySet())));
            return 0;
        }

        ServerLevel level = ctx.getSource().getLevel();
        Registry<ConfiguredFeature<?, ?>> registry = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> feature = registry.getValue(key);

        if (feature == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Configured feature not found: " + key.identifier()));
            return 0;
        }

        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition());

        if (outline) {
            return placeOutline(level, feature, origin, type, ctx.getSource());
        }
        return placeSolid(level, feature, origin, type, ctx.getSource());
    }

    private static int placeSolid(ServerLevel level, ConfiguredFeature<?, ?> feature,
                                  BlockPos origin, String type, CommandSourceStack source) {
        boolean success = feature.place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(), origin);
        if (success) {
            source.sendSuccess(() -> Component.literal(
                    "Spawned " + type + " tree at " + origin.toShortString()), true);
            return 1;
        }
        source.sendFailure(Component.literal(
                "Failed to place " + type + " tree at " + origin.toShortString()));
        return 0;
    }

    /**
     * Places the tree, then replaces all blocks it created with stained glass
     * so the shape is clearly visible without blending into the environment.
     * Yellow glass = logs/trunk, Lime glass = leaves.
     */
    private static int placeOutline(ServerLevel level, ConfiguredFeature<?, ?> feature,
                                    BlockPos origin, String type, CommandSourceStack source) {
        Map<BlockPos, BlockState> before = TreeDebugUtil.snapshotArea(level, origin);

        boolean success = feature.place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(), origin);
        if (!success) {
            source.sendFailure(Component.literal(
                    "Failed to place " + type + " tree at " + origin.toShortString()));
            return 0;
        }

        int count = replaceNewBlocksWithGlass(level, before);
        final int total = count;
        source.sendSuccess(() -> Component.literal(
                "Placed " + type + " tree outline at " + origin.toShortString()
                        + " (" + total + " blocks)"), true);
        return 1;
    }

    private static int replaceNewBlocksWithGlass(ServerLevel level, Map<BlockPos, BlockState> before) {
        int count = 0;
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockPos p = entry.getKey();
            BlockState oldState = entry.getValue();
            BlockState newState = level.getBlockState(p);

            if (oldState == newState) continue;
            count++;

            if (newState.is(BlockTags.LOGS)) {
                level.setBlock(p, Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), 3);
            } else if (newState.is(BlockTags.LEAVES)) {
                level.setBlock(p, Blocks.LIME_STAINED_GLASS.defaultBlockState(), 3);
            }
        }
        return count;
    }
}
