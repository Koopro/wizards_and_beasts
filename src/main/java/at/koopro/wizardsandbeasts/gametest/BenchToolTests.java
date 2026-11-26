package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.item.wand.BenchToolItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The bench's tools: one item per {@link BenchTool}, each laid on the bench at most once, kept across a save, given
 * back as items, and dropped with the blank and the core when the bench is broken. The output slot (a preview of a
 * wand not yet made) is never dropped.
 */
public final class BenchToolTests {

    private static final int Y = 18;

    private BenchToolTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("bench_tools_are_items_one_per_stage",
                "bench tools: every BenchTool has its item, and every tool item names its tool",
                BenchToolTests::itemsPerTool);
        tests.add("bench_tools_lay_once_keep_and_come_back",
                "bench tools: a tool is laid once (a second copy is refused), the set survives a save, and taking "
                        + "them back returns one item per tool and leaves the bench bare",
                BenchToolTests::layKeepTake);
        tests.add("bench_broken_drops_tools_and_work_not_the_preview",
                "bench tools: breaking the bench drops its tools, its blank and its core, never the output preview",
                BenchToolTests::brokenDrops);
    }

    private static void itemsPerTool(GameTestHelper helper) {
        List<BenchToolItem> items = List.of(WandItemRegistry.CARVING_KNIFE.get(), WandItemRegistry.LATHE_CHISEL.get(),
                WandItemRegistry.BORING_BRACE.get(), WandItemRegistry.CORE_TONGS.get(), WandItemRegistry.VARNISH_POT.get());
        check(helper, items.size() == BenchTool.values().length, () -> "tool items and tools differ in number");
        for (BenchTool tool : BenchTool.values()) {
            var item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", tool.itemId()));
            check(helper, item instanceof BenchToolItem t && t.tool() == tool, () -> tool + " has no item of its own: " + item);
        }
        helper.succeed();
    }

    private static WandmakersBenchBlockEntity bench(GameTestHelper helper, int x, int z) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(x, Y, z));
        level.setBlockAndUpdate(pos, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        return (WandmakersBenchBlockEntity) level.getBlockEntity(pos);
    }

    private static void layKeepTake(GameTestHelper helper) {
        WandmakersBenchBlockEntity bench = bench(helper, 2, 2);
        check(helper, bench.placeTool(BenchTool.CHISEL), () -> "the chisel was refused on a bare bench");
        check(helper, !bench.placeTool(BenchTool.CHISEL), () -> "a second chisel was laid");
        check(helper, bench.placeTool(BenchTool.TONGS), () -> "the tongs were refused");
        check(helper, bench.hasTool(BenchTool.CHISEL) && bench.hasTool(BenchTool.TONGS) && !bench.hasTool(BenchTool.KNIFE),
                () -> "wrong tools on the bench: " + bench.toolMask());

        TagValueOutput out = TagValueOutput.createWithContext(new ProblemReporter.Collector(), helper.getLevel().registryAccess());
        bench.saveCustomOnly(out);
        CompoundTag tag = out.buildResult();
        WandmakersBenchBlockEntity copy = new WandmakersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
        copy.setLevel(helper.getLevel());
        copy.loadCustomOnly(TagValueInput.create(new ProblemReporter.Collector(), helper.getLevel().registryAccess(), tag));
        check(helper, copy.toolMask() == bench.toolMask(), () -> "tools did not survive a save: " + tag);

        List<ItemStack> back = bench.takeTools();
        check(helper, back.size() == 2 && back.get(0).is(WandItemRegistry.LATHE_CHISEL.get())
                && back.get(1).is(WandItemRegistry.CORE_TONGS.get()), () -> "took back " + back);
        check(helper, bench.toolMask() == 0, () -> "tools stayed after being taken");
        helper.succeed();
    }

    private static void brokenDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WandmakersBenchBlockEntity bench = bench(helper, 6, 8);
        BlockPos pos = bench.getBlockPos();
        bench.placeTool(BenchTool.KNIFE);
        bench.placeTool(BenchTool.VARNISH);
        bench.getInventory().set(0, ItemResource.of(new ItemStack(WandItemRegistry.WAND_BLANK.get())), 1);
        bench.getInventory().set(2, ItemResource.of(new ItemStack(WandItemRegistry.WAND.get())), 1);
        level.destroyBlock(pos, true);
        helper.runAfterDelay(5, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5));
            check(helper, count(drops, WandItemRegistry.CARVING_KNIFE.get()) == 1, () -> "the knife was not dropped");
            check(helper, count(drops, WandItemRegistry.VARNISH_POT.get()) == 1, () -> "the varnish pot was not dropped");
            check(helper, count(drops, WandItemRegistry.WAND_BLANK.get()) == 1, () -> "the blank was not dropped");
            check(helper, count(drops, WandItemRegistry.WAND.get()) == 0, () -> "the output preview dropped as a free wand");
            check(helper, count(drops, ModBlocks.WANDMAKERS_BENCH_ITEM.get()) == 1, () -> "the bench itself was not dropped once");
            helper.succeed();
        });
    }

    private static int count(List<ItemEntity> drops, net.minecraft.world.item.Item item) {
        return drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }
}
