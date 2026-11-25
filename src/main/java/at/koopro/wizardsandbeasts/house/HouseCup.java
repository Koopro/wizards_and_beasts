package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.network.feedback.NotifyS2CPayload;
import at.koopro.wizardsandbeasts.prophet.DailyProphet;
import at.koopro.wizardsandbeasts.prophet.ProphetNews;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;

/**
 * The House Cup, on a weekly cycle (systems audit D1).
 *
 * <p>Canon (Philosopher's Stone ch.17): points are kept all year and the Cup is given at the end-of-year feast,
 * where the Great Hall is hung in the winner's colours. A school year is far too long for a server, so a cycle
 * here is {@link Rules#cycleDays} in-game days (7 by default, {@code data/wizards_and_beasts/house_cup/rules.json}).
 * When a cycle ends the house with the most points wins, every player online hears the feast (a parchment toast,
 * the feast sound, a chat line in the house colours), the Daily Prophet prints it, and every house starts again.
 *
 * <p>A tie at the top, or a cycle in which nobody earned a point, awards no Cup: canon never shows a shared Cup,
 * and a Cup for nothing would be noise. Server-authoritative: the cycle is the overworld's day count, the same
 * day the Prophet dates its issues by.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HouseCup {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "house.wizards_and_beasts.cup.";
    /** Checked every few seconds; a day is 24000 ticks, so the boundary is never missed by more than this. */
    private static final int CHECK_INTERVAL = 100;

    /** The datapack's word on the cycle. */
    public record Rules(int cycleDays) {
        public static final Rules DEFAULT = new Rules(7);
        public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 3650).optionalFieldOf("cycle_days", 7).forGetter(Rules::cycleDays)
        ).apply(i, Rules::new));
    }

    private static volatile Rules rules = Rules.DEFAULT;

    private HouseCup() {}

    public static Rules rules() {
        return rules;
    }

    /** Replaces the rules (reload, tests). */
    public static void setRules(Rules loaded) {
        rules = loaded;
    }

    /** The cycle {@code day} falls in. */
    public static long cycleOf(long day) {
        return Math.floorDiv(day, rules.cycleDays());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        settle(server, DailyProphet.day(server));
    }

    /**
     * Closes the cycle if {@code day} lies past the one last settled.
     *
     * @return the house that won, or null when no cycle ended or no house won it
     */
    public static @Nullable House settle(MinecraftServer server, long day) {
        HousePoints points = HousePoints.get(server);
        long cycle = cycleOf(day);
        if (points.settledCycle() < 0) {
            // A world (or a save from before the Cup) starts counting now rather than settling a cycle it never saw.
            points.markCycle(cycle);
            return null;
        }
        if (cycle <= points.settledCycle()) {
            return null;
        }
        House winner = leader(points);
        int total = winner == null ? 0 : points.of(winner);
        points.settle(cycle, winner);
        if (winner != null) {
            feast(server, winner, total, day);
        }
        return winner;
    }

    /** The house strictly ahead of all others with more than zero points, or null. */
    static @Nullable House leader(HousePoints points) {
        House best = null;
        int bestPoints = 0;
        boolean tied = false;
        for (House house : House.values()) {
            int n = points.of(house);
            if (n > bestPoints) {
                best = house;
                bestPoints = n;
                tied = false;
            } else if (n == bestPoints && n > 0) {
                tied = true;
            }
        }
        return tied ? null : best;
    }

    private static void feast(MinecraftServer server, House winner, int total, long day) {
        Component house = Component.translatable(winner.nameKey()).withStyle(winner.chat(), ChatFormatting.BOLD);
        Component title = Component.translatable(KEY + "title", house);
        Component body = Component.translatable(KEY + "body", total);
        Component line = Component.translatable(KEY + "feast", house, total);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
            NotifyS2CPayload.send(player, NoticeKind.UNLOCK, title, body);
            player.level().playSound(null, player.blockPosition(), ModSounds.HOUSE_CUP_FEAST.get(), SoundSource.PLAYERS,
                    0.7f, 1.0f);
        }
        ProphetNews.get(server).report(day, "prophet.wizards_and_beasts.story.house_cup",
                Component.translatable(winner.nameKey()).getString(), Integer.toString(total));
        LOGGER.info("[HouseCup] {} won cycle with {} points", winner.id(), total);
    }

    /** Loads {@code house_cup/rules.json}; a pack without it keeps the seven-day default. */
    public static final class Loader extends SimpleJsonResourceReloadListener<Rules> {

        private static final Identifier RULES = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "rules");

        public Loader() {
            super(Rules.CODEC, FileToIdConverter.json("house_cup"));
        }

        @Override
        protected void apply(Map<Identifier, Rules> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            setRules(entries.getOrDefault(RULES, Rules.DEFAULT));
            LOGGER.info("[HouseCup] cycle of {} day(s)", rules.cycleDays());
        }
    }
}
