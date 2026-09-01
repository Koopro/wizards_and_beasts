package at.koopro.wizardsandbeasts.stats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a stat value <em>means</em>, in words, for anything that shows it to a player.
 *
 * <p>Every number here comes back out of {@link StatEffects}. That is the whole point of the class:
 * the character sheet needs to print "Spell Damage +2.6%" beside POWER 42, and the tempting way to
 * do that is a percentage the GUI works out for itself — at which point the sheet and the cast
 * pipeline are two implementations of the same curve, and the first retune makes the sheet lie. The
 * GUI asks for a sentence and never sees a coefficient.
 *
 * <p>Pure: {@link Component} and arithmetic, no Minecraft world state, no client classes. Callable
 * from the HUD, the character sheet, a command, or a test. What it cannot know — this player's
 * heritage cap, whether they are a prodigy, how far into the next point they are — arrives as
 * parameters, because those come from different places on the client and the server.
 *
 * <h2>Reading the effect lines</h2>
 * <ul>
 *   <li>POWER and REFLEXES are multipliers, shown as the percentage change from neutral. REFLEXES
 *       reads {@code +10.0%} at 0 and {@code -12.0%} at 100 — a low-Reflexes wizard really is slower
 *       than the spell's printed cooldown, and hiding that would make the first twelve points look
 *       like they did nothing.</li>
 *   <li>PRECISION is an additive chance delta, so its number is percentage <em>points</em> off the
 *       misfire roll rather than a proportion of it.</li>
 *   <li>WILLPOWER's headline is the resist scalar, shown as a multiplier because that is what it is.
 *       The Resolve pool and its regeneration are real too and appear in the tooltip.</li>
 *   <li>KNOWLEDGE is the tuition discount.</li>
 * </ul>
 */
@NullMarked
public final class StatReadout {

    private static final String PREFIX = "gui.wizards_and_beasts.stat.";

    /**
     * Largest "next point in N" figure worth printing.
     *
     * <p>Past the seventies the S-curve puts five-digit counts on the bullet, and "spell hits x14392"
     * is not a target a player can hold — it reads as the game telling them to stop. Above this the
     * bullet still names the source and simply omits the count, which is the honest answer: at that
     * end the stat is asymptotic and the number would be false precision besides. It also bounds the
     * simulation in {@link #eventsToNextPoint}.
     */
    static final int MAX_COUNTED_EVENTS = 999;

    private StatReadout() {}

    // ── Effect line ──────────────────────────────────────────────────────────────────────────

    /** Short name of what this stat changes: "Spell Damage", "Misfire Chance". */
    public static Component effectLabel(PlayerStat stat) {
        return Component.translatable(PREFIX + "effect." + stat.getId());
    }

    /** The magnitude alone, already signed and suffixed: {@code +2.6%}, {@code x0.71}. */
    public static Component effectValue(PlayerStat stat, int value) {
        return switch (stat) {
            case POWER     -> Component.literal(signedPercent(StatEffects.damageMultiplier(value) - 1f));
            case PRECISION -> Component.literal(signedPercent(StatEffects.misfireDelta(value)));
            case REFLEXES  -> Component.literal(signedPercent(StatEffects.cooldownMultiplier(value) - 1f));
            case WILLPOWER -> Component.literal(multiplier(StatEffects.resistScalar(value)));
            case KNOWLEDGE -> Component.literal(signedPercent(StatEffects.tuitionMultiplier(value) - 1f));
        };
    }

    /**
     * Whether the effect at this value is doing the player a favour, for colouring. Neutral covers
     * "exactly no change", which REFLEXES passes through on its way from slower to faster.
     */
    public static ChatFormatting effectTone(PlayerStat stat, int value) {
        float delta = switch (stat) {
            // Lower is better for these three, so their sign is flipped before the comparison.
            case PRECISION -> -StatEffects.misfireDelta(value);
            case REFLEXES  -> 1f - StatEffects.cooldownMultiplier(value);
            case KNOWLEDGE -> 1f - StatEffects.tuitionMultiplier(value);
            case POWER     -> StatEffects.damageMultiplier(value) - 1f;
            // WILLPOWER has no bad end — the scalar only climbs from its floor — so it is measured
            // against that floor rather than against a neutral it does not have.
            case WILLPOWER -> StatEffects.resistScalar(value) - StatEffects.RESIST_AT_ZERO;
        };
        if (Math.abs(delta) < 1.0e-4f) return ChatFormatting.GRAY;
        return delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    // ── Tooltip ──────────────────────────────────────────────────────────────────────────────

    /**
     * The hover card for one stat.
     *
     * @param value    the stat itself
     * @param progress fraction into the next point, 0–1; ignored for untrainable stats
     * @param cap      highest value this player can reach — {@link PlayerStatsData#MAX_VALUE} for
     *                 everything except POWER, which is capped by heritage
     * @param prodigy  whether this character rolled a prodigy at creation
     */
    public static List<Component> tooltip(PlayerStat stat, int value, float progress, int cap,
                                          boolean prodigy) {
        List<Component> lines = new ArrayList<>(12);

        lines.add(stat.displayName().copy().withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(PREFIX + "desc." + stat.getId()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());

        lines.add(kv(PREFIX + "tooltip.current", plain(Integer.toString(value))));

        // What it does right now, so the number above has a consequence attached to it.
        lines.add(kv(effectLabel(stat), effectValue(stat, value).copy().withStyle(effectTone(stat, value))));
        if (stat == PlayerStat.WILLPOWER) {
            lines.add(kv(PREFIX + "tooltip.resolve_pool",
                    plain(oneDecimal(StatEffects.maxResolve(value)))));
            lines.add(kv(PREFIX + "tooltip.resolve_regen",
                    plain(oneDecimal(StatEffects.resolveRegenPerTick(value) * 20f) + "/s")));
        }

        if (stat.isDerived()) {
            lines.add(Component.empty());
            lines.add(Component.translatable(PREFIX + "tooltip.derived").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable(PREFIX + "tooltip.knowledge_sources")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }

        if (stat.isTrainable()) {
            appendTrainingBlock(lines, stat, value, progress);
        } else {
            lines.add(Component.empty());
            lines.add(Component.translatable(PREFIX + "tooltip.innate").withStyle(ChatFormatting.DARK_GRAY));
        }

        appendCeilingBlock(lines, stat, value, cap);

        if (prodigy && stat == PlayerStat.POWER) {
            lines.add(Component.empty());
            lines.add(Component.translatable(PREFIX + "tooltip.prodigy").withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable(PREFIX + "tooltip.prodigy_desc").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Training percentage, the next point, and how many of each event it takes to get there. */
    private static void appendTrainingBlock(List<Component> lines, PlayerStat stat, int value,
                                            float progress) {
        lines.add(Component.empty());
        lines.add(kv(PREFIX + "tooltip.training",
                plain(Math.round(Math.max(0f, Math.min(1f, progress)) * 100f) + "%")));

        if (value < PlayerStatsData.MAX_VALUE) {
            lines.add(kv(PREFIX + "tooltip.next_point",
                    plain(stat.displayName().getString() + " " + (value + 1))));
        }

        List<StatTraining.Source> sources = StatTraining.Source.forStat(stat);
        if (sources.isEmpty()) return;

        lines.add(Component.empty());
        lines.add(Component.translatable(PREFIX + "tooltip.trained_by").withStyle(ChatFormatting.GRAY));
        for (StatTraining.Source source : sources) {
            int events = eventsToNextPoint(value, progress, source.rawAmount());
            MutableComponent bullet = Component.literal("  • ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(source.description().copy().withStyle(ChatFormatting.GRAY));
            if (events > 0) {
                bullet.append(Component.literal(" ×" + events).withStyle(ChatFormatting.DARK_AQUA));
            }
            lines.add(bullet);
        }
    }

    /**
     * How many more of one event earn the next point, or 0 when there is no honest answer.
     *
     * <p>Simulated against {@link StatTrainingScaler#apply} rather than solved. The closed form —
     * {@code ceil(remaining / perEvent)} — is the obvious way to do this and is wrong: the real
     * accumulator adds a {@code float} several hundred times and the drift in that is enough to land
     * the point an event early, so the sheet would have promised a number the game then did not
     * honour. Running the actual arithmetic cannot disagree with the actual arithmetic.
     *
     * <p>Bounded at {@link #MAX_COUNTED_EVENTS}, which does double duty: it caps the work (this runs
     * per hovered tooltip, not per frame of play) and it is the point past which the figure stops
     * being worth printing anyway.
     *
     * <p>Exact only while this one source is the only thing contributing, which is why it is shown
     * per source rather than as a single number for the stat.
     */
    public static int eventsToNextPoint(int value, float progress, float rawPerEvent) {
        if (value >= PlayerStatsData.MAX_VALUE || rawPerEvent <= 0f) return 0;
        int stat = value;
        float accumulated = Math.max(0f, Math.min(1f, progress));
        for (int events = 1; events <= MAX_COUNTED_EVENTS; events++) {
            StatTrainingScaler.Step step = StatTrainingScaler.apply(stat, accumulated, rawPerEvent);
            if (step.stat() == stat && step.progress() == accumulated) {
                return 0; // the curve pays nothing here; no number of events would do it
            }
            if (step.gained() > 0) return events;
            stat = step.stat();
            accumulated = step.progress();
        }
        return 0;
    }

    /** Says why the stat stops here, when it does. */
    private static void appendCeilingBlock(List<Component> lines, PlayerStat stat, int value, int cap) {
        int effectiveCap = Math.min(cap, PlayerStatsData.MAX_VALUE);
        if (stat.isHeritageCapped()) {
            lines.add(kv(PREFIX + "tooltip.maximum", plain(Integer.toString(effectiveCap))));
            if (value >= effectiveCap) {
                lines.add(Component.translatable(PREFIX + "tooltip.capped_heritage")
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable(PREFIX + "tooltip.capped_heritage_desc", effectiveCap)
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        if (stat.isTrainable() && value >= PlayerStatsData.MAX_VALUE) {
            lines.add(Component.translatable(PREFIX + "tooltip.capped_max").withStyle(ChatFormatting.GOLD));
        }
    }

    // ── formatting ───────────────────────────────────────────────────────────────────────────

    /** {@code Label: value}, the shape every line in the card's body takes. */
    private static MutableComponent kv(String labelKey, Component value) {
        return kv(Component.translatable(labelKey), value);
    }

    private static MutableComponent kv(Component label, Component value) {
        return label.copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(value);
    }

    /** A value with no tone of its own — plain white against the grey label. */
    private static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    /**
     * {@code +2.6%} / {@code -12.0%}. ASCII hyphen rather than U+2212: this is drawn in Minecraft's
     * own font, which has no glyph for the typographic minus and would render a placeholder box in
     * the middle of every negative number on the sheet.
     */
    private static String signedPercent(float fraction) {
        float pct = fraction * 100f;
        // Snap a rounding artefact to zero so a stat sitting exactly on neutral reads "0.0%" rather
        // than "-0.0%", which looks like a bug rather than like nothing happening.
        if (Math.abs(pct) < 0.05f) return "0.0%";
        return String.format(Locale.ROOT, "%+.1f%%", pct);
    }

    private static String multiplier(float value) {
        return String.format(Locale.ROOT, "x%.2f", value);
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
