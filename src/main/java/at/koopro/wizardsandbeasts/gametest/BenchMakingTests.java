package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.BenchMaking;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Stage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Making a wand at the bench, act by act (P4 of the 2026-11-26 remake): turn, bore, marry, seal, first sparks. The
 * performance is fed straight to {@link BenchMaking#resolve} with the beat already run out and a fixed risk roll, so
 * every outcome is seeded: a clean wand, a flawed wand, a cracked blank, a core that does not take.
 */
public final class BenchMakingTests {

    private static final int Y = 22;
    private static final Identifier ASH = Identifier.fromNamespaceAndPath("wizards_and_beasts", "ash");
    private static final Identifier UNICORN = Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn_hair");
    /** Never under a chance: the risk never strikes. */
    private static final float SPARED = 0.9999f;
    /** Under every chance above nothing: the risk strikes whenever it can. */
    private static final float STRUCK = 0f;

    private BenchMakingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("bench_making_clean_hands_make_a_clean_wand",
                "bench making: a Wandmaker with every tool turns, bores, marries and seals on the beat; the wand lands "
                        + "in the output whole (integrity 1), blank and core used up, the bench bare again",
                BenchMakingTests::cleanWand);
        tests.add("bench_making_poor_hands_make_a_flawed_wand",
                "bench making: every beat missed and the risk spared, the wand still comes out, flawed (integrity "
                        + "under 1, not under the floor)",
                BenchMakingTests::flawedWand);
        tests.add("bench_making_a_cracked_blank_is_lost_and_the_core_kept",
                "bench making: a missed turn that cracks loses the blank and ends the making; the core stays",
                BenchMakingTests::crackedBlank);
        tests.add("bench_making_a_core_that_does_not_take_is_spent",
                "bench making: a missed marrying that fails spends the core; the bored blank waits for another",
                BenchMakingTests::coreLost);
        tests.add("bench_making_ignores_strangers_and_early_answers_and_survives_a_save",
                "bench making: another player's or a too-early performance does nothing; a making keeps its stage "
                        + "across a save; an old bench's output preview is not loaded as a wand",
                BenchMakingTests::guardsAndSave);
    }

    // ── setup ────────────────────────────────────────────────────────────────

    private static WandmakersBenchBlockEntity bench(GameTestHelper helper, int x, int z, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, Y, z));
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.WANDMAKERS_BENCH.get().defaultBlockState());
        player.teleportTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        WandmakersBenchBlockEntity bench = (WandmakersBenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        bench.getInventory().set(0, ItemResource.of(blank()), 2);
        bench.getInventory().set(1, ItemResource.of(new ItemStack(WandItemRegistry.UNICORN_HAIR.get())), 2);
        return bench;
    }

    private static ItemStack blank() {
        ItemStack blank = new ItemStack(WandItemRegistry.WAND_BLANK.get());
        blank.set(WandComponents.WAND_WOOD.get(), ASH);
        return blank;
    }

    private static void makeWandmaker(GameTestHelper helper, ServerPlayer player) {
        player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(Map.of(OWLSubject.CHARMS, OWLGrade.E), true, 1, 0L));
        check(helper, VocationManager.commit(player, Identifier.fromNamespaceAndPath("wizards_and_beasts", "wandlore"))
                == VocationManager.CommitResult.OK, () -> "setup: could not commit to Wandlore");
    }

    /**
     * Begins the bench's next act and answers it at once, its beat run out: on every beat (or with a steady hand on
     * the brace) when {@code perfect}, not at all otherwise.
     */
    private static BenchCraft.Act act(GameTestHelper helper, WandmakersBenchBlockEntity bench, ServerPlayer player,
                                      boolean perfect, float roll) {
        BenchMaking.Status status = BenchMaking.begin(bench, player);
        check(helper, status == BenchMaking.Status.READY, () -> "the act did not begin: " + status + " at " + bench.stage());
        WandmakersBenchBlockEntity.PendingAct pending = bench.pending();
        check(helper, pending != null, () -> "no act pending after it began");
        BenchCraft.Odds odds = pending.odds();
        int[] taps = new int[0];
        boolean[] held = new boolean[0];
        if (perfect && odds.act().kind() == BenchCraft.Kind.TAPS) {
            taps = BenchCraft.beat(odds, pending.seed()).beats();
        } else if (perfect) {
            held = steadyHand(odds, pending.seed());
        }
        BenchMaking.resolve(bench, player, pending.seed(), taps, held, pending.startTick() + pending.duration(), roll);
        return odds.act();
    }

    /** Holds the brace whenever the bit is below the middle of the band. */
    private static boolean[] steadyHand(BenchCraft.Odds odds, long seed) {
        boolean[] held = new boolean[BenchCraft.BORE_TICKS];
        for (int i = 0; i < held.length; i++) {
            BenchCraft.BoreFrame[] frames = BenchCraft.bore(odds, seed, held);
            float marker = i == 0 ? 0.5f : frames[i - 1].marker();
            held[i] = marker < frames[i].center();
        }
        return held;
    }

    private static int count(WandmakersBenchBlockEntity bench, int slot) {
        return bench.slot(slot).getCount();
    }

    // ── scenarios ────────────────────────────────────────────────────────────

    private static void cleanWand(GameTestHelper helper) {
        ServerPlayer maker = WizardTestSupport.placeMockPlayer(helper, "BenchMaster", GameType.SURVIVAL);
        try {
            makeWandmaker(helper, maker);
            WandmakersBenchBlockEntity bench = bench(helper, 2, 2, maker);
            for (BenchTool tool : BenchTool.values()) {
                bench.placeTool(tool);
            }
            check(helper, act(helper, bench, maker, true, SPARED) == BenchCraft.Act.TURN && bench.stage() == Stage.TURNED,
                    () -> "after turning: " + bench.stage());
            check(helper, bench.jobWood() != null && bench.jobWood().equals(ASH), () -> "the making forgot its wood");
            act(helper, bench, maker, true, SPARED);
            check(helper, bench.stage() == Stage.BORED, () -> "after boring: " + bench.stage());
            act(helper, bench, maker, true, SPARED);
            check(helper, bench.stage() == Stage.MARRIED && UNICORN.equals(bench.jobCore()) && count(bench, 1) == 1,
                    () -> "after marrying: " + bench.stage() + ", core " + bench.jobCore() + ", cores left " + count(bench, 1));
            float mean = bench.meanScore();
            act(helper, bench, maker, true, SPARED);
            ItemStack wand = bench.slot(2);
            check(helper, wand.is(WandItemRegistry.WAND.get()), () -> "no wand in the output: " + wand);
            check(helper, ASH.equals(WandComponents.getWood(wand)) && UNICORN.equals(WandComponents.getCore(wand)),
                    () -> "the wand is not ash and unicorn: " + wand);
            check(helper, WandComponents.getIntegrity(wand) == 1.0f,
                    () -> "a clean making gave integrity " + WandComponents.getIntegrity(wand) + " (mean " + mean + ")");
            check(helper, bench.stage() == Stage.NONE && count(bench, 0) == 1,
                    () -> "the bench did not clear: " + bench.stage() + ", blanks " + count(bench, 0));
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, maker);
        }
    }

    private static void flawedWand(GameTestHelper helper) {
        ServerPlayer novice = WizardTestSupport.placeMockPlayer(helper, "BenchNovice", GameType.SURVIVAL);
        try {
            WandmakersBenchBlockEntity bench = bench(helper, 6, 2, novice);
            for (int i = 0; i < 4; i++) {
                act(helper, bench, novice, false, SPARED);
            }
            ItemStack wand = bench.slot(2);
            float integrity = WandComponents.getIntegrity(wand);
            check(helper, wand.is(WandItemRegistry.WAND.get()), () -> "no wand after a poor making");
            check(helper, integrity < 1.0f && integrity >= BenchCraft.WORST_INTEGRITY,
                    () -> "a poor making gave integrity " + integrity);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, novice);
        }
    }

    private static void crackedBlank(GameTestHelper helper) {
        ServerPlayer novice = WizardTestSupport.placeMockPlayer(helper, "BenchCracker", GameType.SURVIVAL);
        try {
            WandmakersBenchBlockEntity bench = bench(helper, 2, 6, novice);
            act(helper, bench, novice, false, STRUCK);
            check(helper, bench.stage() == Stage.NONE, () -> "the making went on after a crack: " + bench.stage());
            check(helper, count(bench, 0) == 1, () -> "a crack should lose one blank: " + count(bench, 0) + " left");
            check(helper, count(bench, 1) == 2, () -> "a crack touched the core: " + count(bench, 1) + " left");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, novice);
        }
    }

    private static void coreLost(GameTestHelper helper) {
        ServerPlayer novice = WizardTestSupport.placeMockPlayer(helper, "BenchLoser", GameType.SURVIVAL);
        try {
            WandmakersBenchBlockEntity bench = bench(helper, 6, 6, novice);
            act(helper, bench, novice, true, SPARED);
            act(helper, bench, novice, true, SPARED);
            check(helper, bench.stage() == Stage.BORED, () -> "setup: not bored but " + bench.stage());
            check(helper, act(helper, bench, novice, false, STRUCK) == BenchCraft.Act.MARRY, () -> "not a marrying");
            check(helper, bench.stage() == Stage.BORED && bench.jobCore() == null,
                    () -> "a core that did not take still moved the making: " + bench.stage());
            check(helper, count(bench, 1) == 1 && count(bench, 0) == 2,
                    () -> "cores " + count(bench, 1) + ", blanks " + count(bench, 0));
            // Another core, and the marrying is tried again.
            act(helper, bench, novice, true, SPARED);
            check(helper, bench.stage() == Stage.MARRIED, () -> "the second core did not take: " + bench.stage());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, novice);
        }
    }

    private static void guardsAndSave(GameTestHelper helper) {
        ServerPlayer maker = WizardTestSupport.placeMockPlayer(helper, "BenchOwner", GameType.SURVIVAL);
        ServerPlayer other = WizardTestSupport.placeMockPlayer(helper, "BenchMeddler", GameType.SURVIVAL);
        try {
            WandmakersBenchBlockEntity bench = bench(helper, 4, 10, maker);
            other.teleportTo(maker.getX(), maker.getY(), maker.getZ());
            check(helper, BenchMaking.begin(bench, maker) == BenchMaking.Status.READY, () -> "setup: no turning began");
            WandmakersBenchBlockEntity.PendingAct pending = bench.pending();
            int[] taps = BenchCraft.beat(pending.odds(), pending.seed()).beats();
            long done = pending.startTick() + pending.duration();
            check(helper, BenchMaking.begin(bench, other) == BenchMaking.Status.BUSY, () -> "a second act began over the first");

            BenchMaking.resolve(bench, other, pending.seed(), taps, new boolean[0], done, SPARED);
            check(helper, bench.stage() == Stage.SEATED && bench.pending() != null, () -> "a stranger's answer counted");
            BenchMaking.resolve(bench, maker, pending.seed(), taps, new boolean[0], pending.startTick() + 5, SPARED);
            check(helper, bench.stage() == Stage.SEATED && bench.pending() == null,
                    () -> "an answer before the beat ran out counted: " + bench.stage());

            act(helper, bench, maker, true, SPARED);
            check(helper, bench.stage() == Stage.TURNED, () -> "setup: not turned");
            TagValueOutput out = TagValueOutput.createWithContext(new ProblemReporter.Collector(), helper.getLevel().registryAccess());
            bench.saveCustomOnly(out);
            CompoundTag tag = out.buildResult();
            WandmakersBenchBlockEntity copy = new WandmakersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
            copy.setLevel(helper.getLevel());
            copy.loadCustomOnly(TagValueInput.create(new ProblemReporter.Collector(), helper.getLevel().registryAccess(), tag));
            check(helper, copy.stage() == Stage.TURNED && ASH.equals(copy.jobWood())
                            && Math.abs(copy.meanScore() - bench.meanScore()) < 1e-6f,
                    () -> "the making did not survive a save: " + tag);

            // A bench saved before the remake kept a preview of a wand not yet made under slot_2.
            CompoundTag old = tag.copy();
            old.put("slot_2", ItemStack.CODEC.encodeStart(helper.getLevel().registryAccess().createSerializationContext(
                    net.minecraft.nbt.NbtOps.INSTANCE), new ItemStack(WandItemRegistry.WAND.get())).getOrThrow());
            WandmakersBenchBlockEntity legacy = new WandmakersBenchBlockEntity(bench.getBlockPos(), bench.getBlockState());
            legacy.setLevel(helper.getLevel());
            legacy.loadCustomOnly(TagValueInput.create(new ProblemReporter.Collector(), helper.getLevel().registryAccess(), old));
            check(helper, legacy.slot(2).isEmpty(), () -> "an old bench's preview loaded as a wand");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, maker);
            WizardTestSupport.retire(helper, other);
        }
    }
}
