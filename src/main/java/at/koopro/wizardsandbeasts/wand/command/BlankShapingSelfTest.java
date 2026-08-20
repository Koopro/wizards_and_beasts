package at.koopro.wizardsandbeasts.wand.command;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.registry.WoodSet;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the wand blank's shaping path headlessly, so a dedicated server can prove which logs shape a
 * blank without a human at a keyboard.
 *
 * <p>It exists because the failure it now guards was invisible from the outside: {@code
 * WandBlankItem.wandWoodFromLogBlock} enumerated vanilla log tags only, so every one of the mod's own
 * nine wandwood species returned {@code null} and clicking a blackthorn log simply did nothing. No
 * exception, no log line — "not a wand wood log" is the correct answer for almost every block in the
 * game, so the bug looked exactly like normal operation.
 *
 * <p>Each species is checked through {@code ItemStack.useOn}, the call the server actually makes on a
 * right-click. A fake player supplies the hand; the block is placed and removed per species so the
 * command leaves the world as it found it.
 */
public final class BlankShapingSelfTest {

    private BlankShapingSelfTest() {}

    /** Vanilla families that shape a blank as a fallback, and the wood each is expected to yield. */
    private static final Map<Block, String> VANILLA_DONORS = new LinkedHashMap<>();

    static {
        VANILLA_DONORS.put(Blocks.OAK_LOG, "rowan");
        VANILLA_DONORS.put(Blocks.SPRUCE_LOG, "holly");
        VANILLA_DONORS.put(Blocks.BIRCH_LOG, "hawthorn");
        VANILLA_DONORS.put(Blocks.JUNGLE_LOG, "walnut");
        VANILLA_DONORS.put(Blocks.ACACIA_LOG, "ash");
        VANILLA_DONORS.put(Blocks.DARK_OAK_LOG, "yew");
        VANILLA_DONORS.put(Blocks.MANGROVE_LOG, "willow");
        VANILLA_DONORS.put(Blocks.CHERRY_LOG, "blackthorn");
        VANILLA_DONORS.put(Blocks.CRIMSON_STEM, "elder");
        VANILLA_DONORS.put(Blocks.WARPED_STEM, "vine");
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("blank_test").executes(ctx -> run(ctx.getSource()));
    }

    private static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition()).above(3);
        BlockState restore = level.getBlockState(pos);
        List<String> failures = new ArrayList<>();

        say(source, "--- mod wandwood logs (all four pillar variants) ---");
        for (WoodSet set : ModBlocks.ALL_WOOD_SETS) {
            for (Block variant : List.of(set.log().get(), set.strippedLog().get(),
                    set.wood().get(), set.strippedWood().get())) {
                check(source, level, pos, variant, set.name(), failures);
            }
        }

        say(source, "--- vanilla donor fallbacks ---");
        for (Map.Entry<Block, String> donor : VANILLA_DONORS.entrySet()) {
            check(source, level, pos, donor.getKey(), donor.getValue(), failures);
        }

        say(source, "--- a block that must NOT shape ---");
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        Identifier stone = shape(level, pos);
        if (stone != null) {
            failures.add("stone shaped to " + stone);
        }
        say(source, "stone -> " + stone + (stone == null ? "  OK" : "  FAIL"));

        level.setBlock(pos, restore, 3);
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[blanktest] RESULT: every wandwood shapes correctly")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            for (String failure : failures) {
                source.sendSuccess(() -> Component.literal("[blanktest] FAIL " + failure)
                        .withStyle(ChatFormatting.RED), false);
            }
        }
        return failures.isEmpty() ? 1 : 0;
    }

    /** Place the block, shape a fresh blank on it, and report what wood came back. */
    private static void check(CommandSourceStack source, ServerLevel level, BlockPos pos,
                              Block block, String expected, List<String> failures) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        Identifier got = shape(level, pos);
        boolean ok = got != null && got.getPath().equals(expected);
        if (!ok) {
            failures.add(blockName(block) + " -> " + got + " (expected " + expected + ")");
        }
        say(source, blockName(block) + " -> " + got + (ok ? "  OK" : "  FAIL, expected " + expected));
    }

    /** One shaping attempt through the same call the server makes for a right-click. */
    private static Identifier shape(ServerLevel level, BlockPos pos) {
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(WandItemRegistry.WAND_BLANK.get()));
        ItemStack inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        inHand.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, inHand, hit));
        return WandComponents.getWood(player.getItemInHand(InteractionHand.MAIN_HAND));
    }

    private static String blockName(Block block) {
        return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal("[blanktest] " + line).withStyle(ChatFormatting.YELLOW), false);
    }
}
