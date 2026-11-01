package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Wandmaker's Bench saves its slots: an empty slot writes nothing and reports nothing, a filled one comes back, and
 * a slot an older build saved as a failed partial value reads as empty.
 */
public final class BenchPersistenceTests {

    private BenchPersistenceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("bench_saves_its_slots_without_complaint",
                "bench: empty slots save silently and absent; a filled slot round-trips; an old save's garbled empty "
                        + "slot loads as empty; a tag without a slot clears it, which is how a client learns of it",
                BenchPersistenceTests::savesItsSlots);
    }

    private static void savesItsSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        WandmakersBenchBlockEntity bench = (WandmakersBenchBlockEntity) level.getBlockEntity(pos);

        ProblemReporter.Collector empty = new ProblemReporter.Collector();
        CompoundTag emptyTag = save(helper, bench, empty);
        check(helper, empty.isEmpty(), () -> "saving an empty bench reported: " + empty.getTreeReport());
        check(helper, !emptyTag.contains("slot_0") && !emptyTag.contains("slot_1") && !emptyTag.contains("slot_2"),
                () -> "an empty slot was written: " + emptyTag);

        bench.getInventory().set(1, ItemResource.of(new ItemStack(Items.STICK, 3)), 3);
        ProblemReporter.Collector filled = new ProblemReporter.Collector();
        CompoundTag filledTag = save(helper, bench, filled);
        check(helper, filled.isEmpty(), () -> "saving a filled bench reported: " + filled.getTreeReport());
        check(helper, filledTag.contains("slot_1") && !filledTag.contains("slot_0"),
                () -> "only the filled slot should be written: " + filledTag);

        WandmakersBenchBlockEntity copy = new WandmakersBenchBlockEntity(pos, bench.getBlockState());
        copy.setLevel(level);
        load(helper, copy, filledTag);
        check(helper, copy.getInventory().getAmountAsInt(1) == 3,
                () -> "the filled slot did not come back: " + copy.getInventory().getAmountAsInt(1));
        check(helper, copy.getInventory().getAmountAsInt(0) == 0, () -> "an empty slot came back filled");

        // A client's update tag no longer names an emptied slot, so a tag without it must clear what is there.
        load(helper, copy, emptyTag);
        check(helper, copy.getInventory().getAmountAsInt(1) == 0, () -> "a tag without the slot left it filled");

        // What an older build wrote for an empty slot: the failed encode's partial value.
        CompoundTag garbled = emptyTag.copy();
        CompoundTag air = new CompoundTag();
        air.putString("id", "minecraft:air");
        air.putInt("count", 0);
        garbled.put("slot_0", air);
        load(helper, copy, garbled);
        check(helper, copy.getInventory().getAmountAsInt(0) == 0, () -> "an old save's empty slot loaded filled");
        helper.succeed();
    }

    private static CompoundTag save(GameTestHelper helper, WandmakersBenchBlockEntity bench, ProblemReporter reporter) {
        TagValueOutput output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
        bench.saveCustomOnly(output);
        return output.buildResult();
    }

    private static void load(GameTestHelper helper, WandmakersBenchBlockEntity bench, CompoundTag tag) {
        bench.loadCustomOnly(TagValueInput.create(new ProblemReporter.Collector(), helper.getLevel().registryAccess(), tag));
    }
}
