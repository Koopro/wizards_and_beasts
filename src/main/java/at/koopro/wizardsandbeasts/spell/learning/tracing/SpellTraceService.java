package at.koopro.wizardsandbeasts.spell.learning.tracing;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.spell.SpellSourceItem;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellTraceResultS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * The tracing challenge's server half: issues challenges, judges submissions, and is the only gameplay
 * caller of {@link SpellLearningService#commit}.
 *
 * <h2>Lifecycle</h2>
 * One live challenge per player, held in {@link PlayerScopedState} so a logout drops it with no code
 * here. Death and dimension change cancel it. A newer challenge replaces an older one. A submission
 * must quote the live challenge's id, and the challenge is removed <em>before</em> it is judged, so a
 * second submission — a replay, a double click, a retransmit — finds nothing and does nothing.
 *
 * <h2>What the client is trusted with</h2>
 * Pointer positions and the accessibility flag. Not the verdict, not the time, not the source. The
 * server owns the clock (game time at issue vs. at submission), re-judges the samples against its own
 * path, re-checks the book in hand or the node still allocated, and re-runs every learning gate in
 * {@code commit}. A failed attempt writes nothing and spends nothing.
 */
@NullMarked
public final class SpellTraceService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final PlayerScopedState<TraceChallenge> ACTIVE = PlayerScopedState.create("spell-trace-active");
    /**
     * Spell id → game time the player finished reading a book of it. "Try again" may re-open a trace from a book in
     * hand only while that read is fresh: the client cannot skip the read by asking for the trace directly.
     */
    private static final PlayerScopedState<Map<String, Long>> READ_AT = PlayerScopedState.create("spell-trace-read");
    /** How long a finished read stays good for "Try again": two minutes. */
    public static final long READ_FRESH_TICKS = 2400;

    private SpellTraceService() {
    }

    // ── opening ──────────────────────────────────────────────────────────────

    /**
     * Opens a challenge from a spell source the player has just finished reading.
     *
     * @return the challenge, or null when a gate refused (the player has been told why)
     */
    public static @Nullable TraceChallenge openFromBook(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof SpellSourceItem)) {
            return null;
        }
        Spell spell = SpellSource.spellOf(stack);
        if (spell == null) {
            return null;
        }
        READ_AT.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(spell.getId(), player.level().getGameTime());
        return reopenFromBook(player, hand, spell);
    }

    /** A trace from a book in hand, without the read: only for a read still fresh ({@link #request}). */
    private static @Nullable TraceChallenge reopenFromBook(ServerPlayer player, InteractionHand hand, Spell spell) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof SpellSourceItem item)) {
            return null;
        }
        return open(player, spell, new TraceSource.Book(hand, stack.getItem(), spell.getId(), item.spentOnRead()));
    }

    /**
     * Opens the lesson an allocated node owes for {@code spellId}.
     *
     * @return the challenge, or null when nothing is owed or a gate refused
     */
    public static @Nullable TraceChallenge openLesson(ServerPlayer player, String spellId) {
        String node = SpellLessons.owingNode(player.getData(ModAttachments.SKILL_DATA.get()),
                player.getData(ModAttachments.SPELL_DATA.get()), spellId);
        Spell spell = Spells.byId(spellId);
        if (node == null || spell == null) {
            return null;
        }
        return open(player, spell, new TraceSource.Lesson(node));
    }

    /**
     * A client asking to trace {@code spellId} — Try again, or a click on an owed node. The client names
     * the spell only; the source is whatever the server can find for it right now.
     */
    public static void request(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            return;
        }
        if (openLesson(player, spell.getId()) != null) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SpellSourceItem && spell.getId().equals(SpellSource.spellIdOf(stack))) {
                Map<String, Long> reads = READ_AT.get(player);
                Long readAt = reads == null ? null : reads.get(spell.getId());
                long now = player.level().getGameTime();
                if (readAt == null || now < readAt || now - readAt > READ_FRESH_TICKS) {
                    PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.trace.read_first"));
                    return;
                }
                reopenFromBook(player, hand, spell);
                return;
            }
        }
        PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.trace.no_source"));
    }

    private static @Nullable TraceChallenge open(ServerPlayer player, Spell spell, TraceSource source) {
        SpellLearningService.LearnResult check = SpellLearningService.validateLearnAttempt(player, spell,
                source instanceof TraceSource.Lesson
                        ? SpellLearningService.Route.WEB_LESSON
                        : SpellLearningService.Route.BOOK);
        if (!check.success()) {
            PlayerFeedback.refuse(player, Component.translatable("spell.wizards_and_beasts.learning.refused"),
                    check.text());
            return null;
        }
        long now = player.level().getGameTime();
        long wait = retryWait(player, spell.getId(), now);
        if (wait > 0) {
            PlayerFeedback.actionBar(player, Component.translatable("spell.wizards_and_beasts.trace.retry_wait",
                    SpellSource.writtenName(spell.getId()), (wait + 19) / 20));
            return null;
        }
        // A fresh challenge in place of a live one is a reroll, and a reroll is an attempt given up.
        TraceChallenge replaced = ACTIVE.get(player);
        if (replaced != null) {
            noteFailure(player, replaced, now);
        }
        TraceDifficulty difficulty = TraceDifficulty.forSpell(spell);
        long seed = player.getRandom().nextLong();
        TracePath path = TracePathGenerator.generate(seed, difficulty);
        TraceChallenge challenge = new TraceChallenge(player.getRandom().nextLong(), spell.getId(), difficulty,
                seed, path, now, source);
        ACTIVE.put(player, challenge);
        int[] points = new int[path.size() * 2];
        for (int i = 0; i < path.size(); i++) {
            points[2 * i] = TraceJudge.quantise(path.x(i));
            points[2 * i + 1] = TraceJudge.quantise(path.y(i));
        }
        PacketDistributor.sendToPlayer(player, new SpellTraceOpenS2CPayload(challenge.id(), spell.getId(),
                difficulty.ordinal(), difficulty.timeLimitTicks(path.length()), TraceDifficulty.START_ALLOWANCE_TICKS,
                source.spendsSource(), steadyHand(player), points));
        return challenge;
    }

    // ── judging ──────────────────────────────────────────────────────────────

    /**
     * Judges one submission. An empty sample list is the player closing the screen.
     *
     * @return the outcome, or null when the submission was stale (no live challenge with that id)
     */
    public static @Nullable TraceOutcome submit(ServerPlayer player, long challengeId, boolean assist, double[] xy) {
        TraceChallenge challenge = ACTIVE.get(player);
        if (challenge == null || challenge.id() != challengeId) {
            return null;
        }
        // Single use: gone before anything else can look at it.
        ACTIVE.remove(player);

        long now = player.level().getGameTime();
        if (xy.length == 0) {
            // Closing the screen gives the attempt up: it costs the retry wait, or a closed screen would be a free
            // reroll of a path the player did not like.
            int retry = noteFailure(player, challenge, now);
            send(player, challenge, TraceOutcome.CANCELLED, -1, 0.0, true, "", retry);
            return TraceOutcome.CANCELLED;
        }
        long elapsed = now - challenge.issuedAt();
        TraceProgress.Verdict verdict;
        if (now > challenge.deadline(assist)) {
            verdict = new TraceProgress.Verdict(TraceOutcome.TOO_SLOW, -1, 0.0);
        } else if (elapsed < TraceJudge.minimumTicks(challenge.path())) {
            verdict = new TraceProgress.Verdict(TraceOutcome.TOO_FAST, -1, 0.0);
        } else {
            verdict = TraceJudge.judge(challenge.path(), challenge.difficulty(), assist, xy);
        }
        if (!verdict.outcome().passed()) {
            int retry = noteFailure(player, challenge, now);
            LOGGER.debug("Spell trace failed: player={} spell={} outcome={} seed={} difficulty={}",
                    player.getGameProfile().name(), challenge.spellId(), verdict.outcome(), challenge.seed(),
                    challenge.difficulty());
            send(player, challenge, verdict.outcome(), verdict.failIndex(), verdict.accuracy(), true, "", retry);
            return verdict.outcome();
        }

        // Passed. The source must still be there, then every learning gate gets its say again.
        ItemStack book = ItemStack.EMPTY;
        if (challenge.source() instanceof TraceSource.Book src) {
            book = player.getItemInHand(src.hand());
            Spell written = SpellSource.spellOf(book);
            if (book.getItem() != src.item() || written == null || !written.getId().equals(src.spellId())) {
                send(player, challenge, TraceOutcome.SOURCE_GONE, -1, verdict.accuracy(), false, "");
                return TraceOutcome.SOURCE_GONE;
            }
        } else if (challenge.source() instanceof TraceSource.Lesson src) {
            String owing = SpellLessons.owingNode(player.getData(ModAttachments.SKILL_DATA.get()),
                    player.getData(ModAttachments.SPELL_DATA.get()), challenge.spellId());
            if (!src.nodeId().equals(owing)) {
                send(player, challenge, TraceOutcome.SOURCE_GONE, -1, verdict.accuracy(), false, "");
                return TraceOutcome.SOURCE_GONE;
            }
        }
        Spell spell = Spells.byId(challenge.spellId());
        SpellLearningService.Route route = challenge.source() instanceof TraceSource.Lesson
                ? SpellLearningService.Route.WEB_LESSON
                : SpellLearningService.Route.BOOK;
        SpellLearningService.LearnResult result = SpellLearningService.commit(player, spell, route);
        if (!result.success()) {
            send(player, challenge, TraceOutcome.REFUSED, -1, verdict.accuracy(), true,
                    at.koopro.wizardsandbeasts.network.spell.SpellTraceResultS2CPayload.encodeDetail(result.text()));
            return TraceOutcome.REFUSED;
        }
        // Spent only now: a page refused by a gate, or traced badly, has not been studied to pieces.
        if (challenge.source().spendsSource() && !player.getAbilities().instabuild) {
            book.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.6f, 1.6f);
        PlayerFeedback.unlocked(player,
                Component.translatable("spell.wizards_and_beasts.learning.learned",
                        SpellSource.writtenName(challenge.spellId())),
                Component.translatable("spell.wizards_and_beasts.learning.learned.detail"));
        send(player, challenge, TraceOutcome.PASSED, -1, verdict.accuracy(), !challenge.source().spendsSource(), "");
        return TraceOutcome.PASSED;
    }

    /** Cancels the player's live challenge, if any, and tells their screen. */
    public static void cancel(ServerPlayer player) {
        TraceChallenge challenge = ACTIVE.remove(player);
        if (challenge != null) {
            int retry = noteFailure(player, challenge, player.level().getGameTime());
            send(player, challenge, TraceOutcome.CANCELLED, -1, 0.0, true, "", retry);
        }
    }

    /** The "Steady hand" setting the server judges by. */
    public static boolean steadyHand(ServerPlayer player) {
        return player.getData(ModAttachments.TRACE_MEMORY.get()).steadyHand();
    }

    /** Changes the "Steady hand" setting; it applies to the next challenge opened, not to one already live. */
    public static void setSteadyHand(ServerPlayer player, boolean steady) {
        if (ACTIVE.get(player) != null) {
            return;
        }
        player.setData(ModAttachments.TRACE_MEMORY.get(),
                player.getData(ModAttachments.TRACE_MEMORY.get()).withSteadyHand(steady));
    }

    /** The player's live challenge. For tests and diagnostics; gameplay never needs to look. */
    public static @Nullable TraceChallenge active(ServerPlayer player) {
        return ACTIVE.get(player);
    }

    /**
     * Game tests only: moves the live challenge's issue time back, standing in for the seconds a real
     * player spends tracing. A test submits in the same tick it opened, which is — correctly — too fast
     * for any hand.
     */
    public static void backdateForTest(ServerPlayer player, long ticks) {
        TraceChallenge c = ACTIVE.get(player);
        if (c != null) {
            ACTIVE.put(player, new TraceChallenge(c.id(), c.spellId(), c.difficulty(), c.seed(), c.path(),
                    c.issuedAt() - ticks, c.source()));
        }
    }

    /** Ticks until {@code spellId} may be attempted again; 0 when it may be now. */
    public static long retryWait(ServerPlayer player, String spellId, long now) {
        return player.getData(ModAttachments.TRACE_MEMORY.get()).waitFor(spellId, now);
    }

    private static int noteFailure(ServerPlayer player, TraceChallenge challenge, long now) {
        int delay = challenge.difficulty().retryDelayTicks();
        if (delay > 0) {
            player.setData(ModAttachments.TRACE_MEMORY.get(), player.getData(ModAttachments.TRACE_MEMORY.get())
                    .withRetry(challenge.spellId(), now + delay, now));
        }
        return delay;
    }

    private static void send(ServerPlayer player, TraceChallenge challenge, TraceOutcome outcome, int failIndex,
                             double accuracy, boolean sourceKept, String detail) {
        send(player, challenge, outcome, failIndex, accuracy, sourceKept, detail, 0);
    }

    private static void send(ServerPlayer player, TraceChallenge challenge, TraceOutcome outcome, int failIndex,
                             double accuracy, boolean sourceKept, String detail, int retryTicks) {
        PacketDistributor.sendToPlayer(player, new SpellTraceResultS2CPayload(challenge.id(), outcome.ordinal(),
                failIndex, (int) Math.round(accuracy * 1000), retryTicks, sourceKept, detail));
    }

    /** Death and dimension change end a challenge: the book may be on the floor of another world. */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onDeath(LivingDeathEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                cancel(player);
            }
        }

        @SubscribeEvent
        public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                cancel(player);
            }
        }
    }
}
