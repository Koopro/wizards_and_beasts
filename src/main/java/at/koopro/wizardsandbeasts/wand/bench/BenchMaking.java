package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.item.wand.WandBlankItem;
import at.koopro.wizardsandbeasts.item.wand.WandCoreMaterialItem;
import at.koopro.wizardsandbeasts.network.wand.BenchBeatStartPayload;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Act;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Hands;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Odds;
import at.koopro.wizardsandbeasts.wand.making.BenchCraft.Stage;
import at.koopro.wizardsandbeasts.wand.making.CoreMaking;
import at.koopro.wizardsandbeasts.wand.making.WandmakingRules;
import at.koopro.wizardsandbeasts.wand.making.WandmakingSpec;
import at.koopro.wizardsandbeasts.wand.making.WoodMaking;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement;
import at.koopro.wizardsandbeasts.wand.rules.WandRules;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * The bench's making, server side: what the next act is and what it risks, starting it (the player's client plays
 * its beat), and resolving the performance the client sends back. The rules are {@link BenchCraft}; this gathers
 * their inputs (the maker, the tools, the wood and core) and applies the answer to the bench.
 *
 * <p>The client only reports which ticks the use key was pressed or held on. The server rebuilds the same beat from
 * the seed it chose, scores it, and rolls the risk with its own random source.
 */
@NullMarked
public final class BenchMaking {

    /** What the bench says about its next act. {@link #READY}: the act can begin. */
    public enum Status {
        READY, NO_BLANK, BLANK_UNSHAPED, NO_CORE, NO_PAIR, WITHDRAWN, OUTPUT_FULL, BUSY;

        public String key() {
            return "wandcraft.bench.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The next act, its odds, and whether it may begin. {@code odds} is empty when the bench cannot tell yet. */
    public record Next(Act act, Status status, Optional<Odds> odds) {}

    /** Ticks a started act waits for its performance after its beat has run out, before the bench forgets it. */
    static final int RESULT_GRACE = 200;
    /** How far from the bench a performance still counts. */
    private static final double REACH_SQR = 8.0 * 8.0;

    private BenchMaking() {}

    /** The maker at the bench, as the rules see them. */
    public static Hands hands(ServerPlayer player) {
        boolean wandmaker = VocationHelper.hasGrantedAbility(player, WandRefinement.CARE_FOR_OTHERS);
        PlayerProfessionData career = player.getData(ModAttachments.PROFESSION_DATA.get());
        boolean careerWandmaker = career.profession() == Profession.WANDMAKER;
        var skills = SkillSystemAPI.getSkillData(player);
        int lore = 0;
        for (String node : BenchCraft.LORE_NODES) {
            if (skills.hasSkill(node)) {
                lore++;
            }
        }
        return new Hands(wandmaker, careerWandmaker, lore);
    }

    /** What the bench would do next for this player. */
    public static Next next(WandmakersBenchBlockEntity bench, ServerPlayer player) {
        Act act = Act.next(bench.stage());
        ServerLevel level = (ServerLevel) bench.getLevel();
        if (bench.pending() != null && !bench.pendingExpired(level.getGameTime())) {
            return new Next(act, Status.BUSY, Optional.empty());
        }
        Identifier wood = bench.stage() == Stage.NONE ? woodOf(bench.slot(0)) : bench.jobWood();
        if (bench.stage() == Stage.NONE && bench.slot(0).isEmpty()) {
            return new Next(act, Status.NO_BLANK, Optional.empty());
        }
        if (wood == null) {
            return new Next(act, Status.BLANK_UNSHAPED, Optional.empty());
        }
        Optional<WoodMaking> woodMaking = WandmakingRules.woodMaking(level.registryAccess(), wood);
        if (woodMaking.isEmpty()) {
            return new Next(act, Status.NO_PAIR, Optional.empty());
        }
        if (bench.stage() == Stage.NONE && !bench.slot(2).isEmpty()) {
            return new Next(act, Status.OUTPUT_FULL, Optional.empty());
        }
        Identifier core = act == Act.MARRY ? WandCoreMaterialItem.getCoreKey(bench.slot(1)) : bench.jobCore();
        if (act == Act.MARRY && core == null) {
            return new Next(act, Status.NO_CORE, Optional.empty());
        }
        CoreMaking coreMaking = core == null ? CoreMaking.DEFAULT : WandmakingRules.coreMaking(level.registryAccess(), core);
        int affinity = 0;
        if (core != null) {
            Optional<WandmakingSpec> spec = WandmakingRules.spec(level.registryAccess(), wood, core);
            if (spec.isEmpty()) {
                return new Next(act, Status.NO_PAIR, Optional.empty());
            }
            if (!WandRules.mayMake(wood, core)) {
                return new Next(act, Status.WITHDRAWN, Optional.empty());
            }
            affinity = spec.get().affinity();
        }
        Odds odds = BenchCraft.odds(act, hands(player), bench.toolMask(), woodMaking.get().difficulty(), coreMaking, affinity);
        return new Next(act, Status.READY, Optional.of(odds));
    }

    /**
     * Begins the next act for this player: seats a blank on a bare bench, fixes the odds, picks the seed, and sends
     * the beat to the player's client. Returns the status; anything but {@link Status#READY} means nothing began.
     */
    public static Status begin(WandmakersBenchBlockEntity bench, ServerPlayer player) {
        Next next = next(bench, player);
        if (next.status() != Status.READY || next.odds().isEmpty()) {
            return next.status();
        }
        ServerLevel level = (ServerLevel) bench.getLevel();
        if (bench.stage() == Stage.NONE) {
            bench.seat(woodOf(bench.slot(0)));
        }
        long seed = level.getRandom().nextLong();
        Odds odds = next.odds().get();
        int duration = BenchCraft.beat(odds, seed).duration();
        bench.setPending(new WandmakersBenchBlockEntity.PendingAct(player.getUUID(), odds, seed, level.getGameTime(), duration));
        player.closeContainer();
        PacketDistributor.sendToPlayer(player, new BenchBeatStartPayload(bench.getBlockPos(), seed, odds));
        level.playSound(null, bench.getBlockPos(), ModSounds.BENCH_KNOCK.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        return Status.READY;
    }

    /**
     * The player's client finished an act's beat. Scores it, rolls its risk, and moves the making on (or loses the
     * blank or the core). A performance for an act this bench did not start, from someone else, too early, too late
     * or from too far away is ignored; the act then stays undone and can be started again.
     */
    public static void resolve(WandmakersBenchBlockEntity bench, ServerPlayer player, long seed, int[] taps, boolean[] held) {
        ServerLevel level = (ServerLevel) bench.getLevel();
        resolve(bench, player, seed, taps, held, level.getGameTime(), level.getRandom().nextFloat());
    }

    /**
     * {@link #resolve(WandmakersBenchBlockEntity, ServerPlayer, long, int[], boolean[])} at a given clock and with a
     * given risk roll (0 to 1; the risk strikes when the roll is under its chance). For GameTests: a seeded outcome,
     * and an act resolved without waiting out its beat.
     */
    public static void resolve(WandmakersBenchBlockEntity bench, ServerPlayer player, long seed, int[] taps, boolean[] held,
                               long now, float roll) {
        WandmakersBenchBlockEntity.PendingAct pending = bench.pending();
        ServerLevel level = (ServerLevel) bench.getLevel();
        if (pending == null || pending.seed() != seed || !pending.player().equals(player.getUUID())) {
            return;
        }
        bench.setPending(null);
        // The client counts its own ticks from the moment the beat reached it, so it cannot finish before the
        // server's clock has run the beat (less a little for a client tick running ahead of a lagging server).
        if (now - pending.startTick() < pending.duration() - 20 || now - pending.startTick() > pending.duration() + RESULT_GRACE
                || player.distanceToSqr(bench.getBlockPos().getCenter()) > REACH_SQR) {
            return;
        }
        Odds odds = pending.odds();
        float score = BenchCraft.score(odds, seed, taps, held);
        BlockPos pos = bench.getBlockPos();
        boolean struck = roll < BenchCraft.chance(odds, score);
        int percent = Math.round(score * 100f);
        if (struck && odds.act().risk() == BenchCraft.Risk.CRACK) {
            bench.crack();
            level.playSound(null, pos, ModSounds.BENCH_CRACK.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.2, 0.1, 0.2, 0.05);
            player.displayClientMessage(Component.translatable("wandcraft.bench.outcome.crack", percent), false);
            return;
        }
        if (struck && odds.act().risk() == BenchCraft.Risk.CORE_LOST) {
            bench.loseCore();
            level.playSound(null, pos, ModSounds.BENCH_CORE_LOST.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.15, 0.1, 0.15, 0.01);
            player.displayClientMessage(Component.translatable("wandcraft.bench.outcome.core_lost", percent), false);
            return;
        }
        if (odds.act() == Act.MARRY) {
            Identifier core = WandCoreMaterialItem.getCoreKey(bench.slot(1));
            if (core == null) {
                // The core was taken off between the start and the end: the act did not happen.
                return;
            }
            bench.marry(core);
        }
        bench.scoreAct(score);
        if (odds.act() == Act.SEAL) {
            firstSparks(bench, player);
            return;
        }
        level.playSound(null, pos, odds.act() == Act.MARRY ? ModSounds.BENCH_TAKE.get() : ModSounds.BENCH_KNOCK.get(),
                SoundSource.BLOCKS, 0.8f, 1.0f);
        player.displayClientMessage(Component.translatable("wandcraft.bench.outcome.done",
                Component.translatable(odds.act().key()), percent), true);
    }

    /**
     * The sealed wand's first spell (GoF ch.18): the wand is made and lands in the output slot, its integrity from
     * the making's mean score, and sparks fly in proportion.
     */
    private static void firstSparks(WandmakersBenchBlockEntity bench, ServerPlayer player) {
        ServerLevel level = (ServerLevel) bench.getLevel();
        Identifier wood = bench.jobWood();
        Identifier core = bench.jobCore();
        Optional<WandmakingSpec> spec = wood == null || core == null ? Optional.empty()
                : WandmakingRules.spec(level.registryAccess(), wood, core);
        if (spec.isEmpty()) {
            bench.clearJob();
            return;
        }
        float mean = bench.meanScore();
        float integrity = BenchCraft.integrity(mean);
        WandFlexibility flex = WandFlexibility.values()[bench.getSelectedFlexibilityOrdinal()];
        ItemStack wand = spec.get().createWand(flex, spec.get().rollLength(level.getRandom()), integrity);
        bench.finish(wand);
        BlockPos pos = bench.getBlockPos();
        bench.playSparks();
        bench.startSparks(integrity >= 1.0f, mean);
        level.playSound(null, pos, integrity >= 1.0f ? ModSounds.BENCH_SPARKS.get() : ModSounds.BENCH_FIZZLE.get(),
                SoundSource.BLOCKS, 1.0f, integrity >= 1.0f ? 0.9f + 0.2f * mean : 1.0f);
        player.displayClientMessage(Component.translatable(integrity >= 1.0f
                ? "wandcraft.bench.outcome.clean" : "wandcraft.bench.outcome.flawed",
                Math.round(integrity * 100f)), false);
        // A wand finished at the bench is a Wandmaker's day of work.
        CareerWork.record(player, CareerWork.Kind.WANDCRAFT);
    }

    private static final DustParticleOptions RED = new DustParticleOptions(0xC8202A, 1.1f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xF0C040, 1.1f);
    private static final DustParticleOptions ASH = new DustParticleOptions(0x6A6058, 1.0f);

    /** Ticks the first sparks stream for. */
    static final int SPARK_TICKS = 16;

    /**
     * One tick of a new wand's first spell. A clean one answers as Harry's did in Ollivanders (PS ch.5): "a stream of
     * red and gold sparks shot from the end like a firework", so each tick lays a little more of a rising column, fuller
     * the better the making, with a firework glitter at its head. A flawed one sputters a few dull sparks and smoke.
     *
     * @param step 0 to {@link #SPARK_TICKS} - 1
     */
    static void sparkTick(ServerLevel level, BlockPos pos, boolean clean, float mean, int step) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.1;
        double z = pos.getZ() + 0.5;
        if (!clean) {
            if (step % 4 == 0) {
                level.sendParticles(ASH, x, y + 0.1, z, 2 + Math.round(3 * mean), 0.08, 0.05, 0.08, 0.0);
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.08, 0.05, 0.08, 0.01);
            }
            return;
        }
        // The column climbs with the ticks: a stream, not a burst. One packet per colour: the spread places the
        // sparks up the column.
        double head = y + 0.1 + step * (0.1 + 0.06 * mean);
        double mid = (y + head) / 2;
        double rise = (head - y) / 3;
        int perColour = 1 + Math.round(2 * mean);
        level.sendParticles(RED, x, mid, z, perColour, 0.05, rise, 0.05, 0);
        level.sendParticles(GOLD, x, mid, z, perColour, 0.05, rise, 0.05, 0);
        if (step % 2 == 0) {
            level.sendParticles(ParticleTypes.FIREWORK, x, head, z, 1 + Math.round(2 * mean), 0.08, 0.04, 0.08, 0.05);
        }
    }

    /** The wood a blank was carved from, or {@code null} for an unshaped blank or no blank. */
    static @Nullable Identifier woodOf(ItemStack blank) {
        return blank.getItem() instanceof WandBlankItem ? WandComponents.getWood(blank) : null;
    }
}
