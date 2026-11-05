package at.koopro.wizardsandbeasts.creature.ecology;

import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A creature's ecological working memory: what it last perceived, what it decided and since when, where home is,
 * its needs, what it is cooling down from, and the water, food and remains it knows of.
 *
 * <p><b>Saved:</b> home, needs and fed-until ({@link #CODEC}), and only for a creature that has ever looked around —
 * so a creature reloaded from disk goes back to the same tree and is as hungry as it was. <b>Not saved:</b> the
 * perception, the decision, cooldowns and known places (it looks around again within a second of loading), and the
 * personality, which is derived from the UUID and so never needs saving. Never synced to clients.
 */
@NullMarked
public final class EcologyState implements EcologyCommitment.Cooldowns {

    private static final EcologyIntent[] INTENTS = EcologyIntent.values();

    @Nullable Perception perception;
    EcologyBrain.Decision decision = EcologyBrain.Decision.NONE;
    /** Game time the current decision was taken up. */
    long heldSince;
    @Nullable Vec3 home;
    long fedUntil;
    Perception.@Nullable Stimulus stimulus;
    long stimulusUntil;
    @Nullable UUID preyId;
    @Nullable UUID playerId;
    long nextActionTick;
    /** EVENT tier: something happened (a stimulus, damage, an interaction) that deserves a look before the next one. */
    boolean wake;
    /** When it last looked around, so a burst of events cannot make it look around every tick. */
    long lastThinkTick = Long.MIN_VALUE / 2;

    final EcologyNeeds needs = new EcologyNeeds();
    /** Game time needs were last advanced; {@code MIN_VALUE} until the first look (and after every load). */
    long needsAt = Long.MIN_VALUE;
    @Nullable EcologyPersonality personality;
    /** The profile the personality was drawn from; a datapack reload that changes the ranges redraws it. */
    @Nullable EcologyProfile personalityFrom;
    /** Where the water it is drinking is, to look at (the find's stand position is {@link #waterAt}). */
    @Nullable Vec3 waterLook;
    @Nullable Vec3 forageLook;

    private final long[] cooldownUntil = new long[INTENTS.length];
    private final byte[] failures = new byte[INTENTS.length];

    /** Places it knows of, each good until its tick; found on the SLOW tier, re-checked on arrival. */
    @Nullable Vec3 waterAt;
    long waterUntil;
    @Nullable Vec3 forageAt;
    long forageUntil;
    @Nullable Vec3 carrionAt;
    long carrionUntil;
    /** The last remains it went over, so it does not go back to the same ones. */
    @Nullable Vec3 carrionEaten;

    public EcologyIntent intent() {
        return decision.intent();
    }

    public EcologyBrain.Decision decision() {
        return decision;
    }

    public long heldSince() {
        return heldSince;
    }

    public @Nullable Perception perception() {
        return perception;
    }

    public @Nullable Vec3 home() {
        return home;
    }

    public EcologyNeeds needs() {
        return needs;
    }

    public @Nullable EcologyPersonality personality() {
        return personality;
    }

    public boolean fedAt(long now) {
        return now < fedUntil;
    }

    /** Records a reaction answer, in force until {@code until}. A stronger answer (flight) is not overwritten. */
    public void react(Perception.Stimulus value, long now, long until) {
        boolean current = stimulus != null && now < stimulusUntil;
        if (current && stimulus.response() == CreatureReaction.Response.FLEE
                && value.response() != CreatureReaction.Response.FLEE) {
            return;
        }
        stimulus = value;
        stimulusUntil = until;
    }

    /**
     * Asks for a look around at the next goal evaluation instead of the next scheduled one (the EVENT tier,
     * documentation/CREATURE_AI_SCHEDULING.md). Costs a flag; the look itself is the usual one bounded query, and no
     * sooner than {@code EcologyGoal.EVENT_MIN_GAP} ticks after the last.
     */
    public void wake() {
        wake = true;
    }

    public boolean awake() {
        return wake;
    }

    // ── commitment ──────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean cooling(EcologyIntent intent, long now) {
        return now < cooldownUntil[intent.ordinal()];
    }

    /** It ended normally (or ran out its time): rest it for its end cooldown. */
    public void ended(EcologyIntent intent, long now, boolean timedOut) {
        int ticks = timedOut ? Math.max(intent.endCooldown(), intent.failCooldown()) : intent.endCooldown();
        cooldownUntil[intent.ordinal()] = Math.max(cooldownUntil[intent.ordinal()], now + ticks);
    }

    // ── recent history (debugging: the admin creature report and test failure messages) ─────────────

    private static final int HISTORY = 8;
    private final long[] historyTick = new long[HISTORY];
    private final byte[] historyIntent = new byte[HISTORY];
    private final boolean[] historyFailed = new boolean[HISTORY];
    private int historyNext;

    /** Records a change of intent (or a failure of the current one). Three array writes. */
    void remember(EcologyIntent intent, long now, boolean failed) {
        historyTick[historyNext] = now;
        historyIntent[historyNext] = (byte) intent.ordinal();
        historyFailed[historyNext] = failed;
        historyNext = (historyNext + 1) % HISTORY;
    }

    /** The last few changes, oldest first: {@code "120:DRINK 300:DRINK! 301:NONE"} ({@code !} = failed). */
    public String history() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < HISTORY; i++) {
            int at = (historyNext + i) % HISTORY;
            if (historyTick[at] == 0) {
                continue;
            }
            out.append(out.isEmpty() ? "" : " ").append(historyTick[at]).append(':')
                    .append(INTENTS[historyIntent[at]].name()).append(historyFailed[at] ? "!" : "");
        }
        return out.toString();
    }

    /** Not before {@code until}: an intent held back from the start (a groom straight after arriving). */
    public void coolUntil(EcologyIntent intent, long until) {
        cooldownUntil[intent.ordinal()] = Math.max(cooldownUntil[intent.ordinal()], until);
    }

    /** It could not be done (no path, no progress, target gone): back off, longer each time in a row. */
    public void failed(EcologyIntent intent, long now) {
        remember(intent, now, true);
        int i = intent.ordinal();
        failures[i] = (byte) Math.min(failures[i] + 1, 4);
        cooldownUntil[i] = now + ((long) intent.failCooldown() << (failures[i] - 1));
    }

    /** It worked: the next failure starts from the short cooldown again. */
    public void succeeded(EcologyIntent intent) {
        failures[intent.ordinal()] = 0;
    }

    public int failures(EcologyIntent intent) {
        return failures[intent.ordinal()];
    }

    /** Water it knows of: where to stand and what to look at, good until {@code until}. */
    public void rememberWater(Vec3 stand, Vec3 look, long until) {
        waterAt = stand;
        waterLook = look;
        waterUntil = until;
    }

    public @Nullable Vec3 waterAt() {
        return waterAt;
    }

    public @Nullable Vec3 forageAt() {
        return forageAt;
    }

    public @Nullable Vec3 carrionAt() {
        return carrionAt;
    }

    /** Something died at {@code at}: a scavenger remembers it until {@code until}. */
    public void noteCarrion(Vec3 at, long until) {
        carrionAt = at;
        carrionUntil = until;
    }

    // ── saving ──────────────────────────────────────────────────────────────────────────────────

    /** Whether there is anything worth writing: it has a home or needs, i.e. it has looked around. */
    public boolean worthSaving() {
        return home != null || !needs.saved().isEmpty() || fedUntil > 0;
    }

    private record Saved(Optional<Vec3> home, Map<EcologyNeed, Float> needs, long fedUntil) {}

    private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.optionalFieldOf("home").forGetter(Saved::home),
            EcologyNeeds.SAVED_CODEC.optionalFieldOf("needs", Map.of()).forGetter(Saved::needs),
            Codec.LONG.optionalFieldOf("fed_until", 0L).forGetter(Saved::fedUntil)
    ).apply(i, Saved::new));

    /** What is saved with the creature. */
    public static final MapCodec<EcologyState> CODEC = SAVED.fieldOf("ecology").xmap(saved -> {
        EcologyState state = new EcologyState();
        state.home = saved.home().orElse(null);
        state.needs.load(saved.needs());
        state.fedUntil = saved.fedUntil();
        return state;
    }, state -> new Saved(Optional.ofNullable(state.home), state.needs.saved(), state.fedUntil));
}
