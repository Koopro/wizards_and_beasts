package at.koopro.wizardsandbeasts.spell.revelio;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.render.outline.OutlineStyle;
import at.koopro.wizardsandbeasts.render.outline.SpellOutlines;
import at.koopro.wizardsandbeasts.spell.core.JsonSpell;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Revelio: for a few seconds, everything the caster can see nearby that is alive or worth finding is
 * outlined in the spell's own colour.
 *
 * <p>This class decides <em>how far</em> and <em>how long</em>, asks {@link RevelioScan} what is there,
 * and hands the answer to {@link SpellOutlines} in one {@link OutlineStyle}, so entities and blocks share
 * exactly one colour. It draws nothing and remembers nothing: expiry, overlap, fading, syncing and cleanup
 * all belong to the outline services.
 *
 * <h2>Who sees it</h2>
 *
 * <p>The two halves reach different audiences, because the outline paths do:
 * <ul>
 *   <li><b>Entities</b> — everyone. Entity outlines are broadcast; there is no per-viewer entity outline.
 *       That suits the canon reading of the charm, which makes hidden things <em>visible</em> rather than
 *       whispering their location to the caster — so a cloaked wizard caught in it is outlined for the whole
 *       room.</li>
 *   <li><b>Blocks</b> — the caster only. A chest behind a bookcase is something the caster learned.</li>
 * </ul>
 *
 * <p>Casting again while a reveal is still showing never shortens it (see {@link SpellOutlines}).
 *
 * <p>Runs as a SELF utility rule in {@code SpellExecutor}, after the pipeline has already applied the
 * cast gates, cooldown, misfire roll and sound.
 */
@NullMarked
public final class Revelio {

    public static final double DEFAULT_RADIUS = 14.0;
    public static final int DEFAULT_DURATION_TICKS = 140;

    /** The block scan is a cube of this radius, so the cost is cubic: a datapack typo must not make it 200. */
    public static final double MAX_RADIUS = 24.0;
    public static final int MAX_DURATION_TICKS = 600;

    private Revelio() {}

    /**
     * Casts Revelio for {@code caster}.
     *
     * @return whether anything was revealed. An empty cast is not a successful one, so it earns no
     *         proficiency: otherwise the cheapest way to master the charm is to cast it at an empty field.
     */
    public static boolean cast(ServerLevel level, ServerPlayer caster, Spell spell) {
        Settings settings = Settings.of(spell);
        RevelioScan.Result found = RevelioScan.scan(level, caster, settings.radius());
        if (found.isEmpty()) {
            PlayerFeedback.actionBar(caster, Component.translatable("spell.wizards_and_beasts.revelio.nothing"));
            return false;
        }
        // Vivid rather than raw: the spell's pale 0xFFFFAA washes out to grey in both outline kinds.
        OutlineStyle style = OutlineStyle.forSpell(spell.getColor(), settings.durationTicks());
        SpellOutlines.highlightEntities(found.entities(), style);
        SpellOutlines.highlightBlocks(caster, found.blocks(), style);
        PlayerFeedback.actionBar(caster,
                Component.translatable("spell.wizards_and_beasts.revelio.revealed", found.total()));
        return true;
    }

    /**
     * How far one cast reaches and how long its outlines last, from {@code revelio.json}'s {@code range} and
     * {@code baseEffectDurationTicks}; the defaults above when a field is absent, clamped either way.
     *
     * <h2>Scaling hook</h2>
     *
     * <p>Both numbers are flat for now. The multipliers to scale them already exist at the call site —
     * {@code SpellExecutor.dispatchGeneric} holds {@code wand.rangeFor(spell)} and
     * {@code ctx.scalingProfile().durationMult()} (proficiency) — but SELF utility rules are handed only
     * {@code (level, caster, spell)}. To scale Revelio, pass those two into {@link #cast} and multiply them in
     * {@link #of(float, int)} <em>before</em> the clamp, so a master's wand still cannot scan past
     * {@link #MAX_RADIUS}. A skill node would go through the same two multipliers rather than a third.
     */
    public record Settings(double radius, int durationTicks) {

        public static Settings of(Spell spell) {
            return spell instanceof JsonSpell json
                    ? of(json.definition().range(), json.definition().baseEffectDurationTicks())
                    : of(0.0f, 0);
        }

        /** Zero or negative means "not authored": fall back to the default rather than to nothing. */
        public static Settings of(float range, int durationTicks) {
            double radius = range > 0.0f ? Math.min(range, MAX_RADIUS) : DEFAULT_RADIUS;
            int duration = durationTicks > 0 ? Math.min(durationTicks, MAX_DURATION_TICKS) : DEFAULT_DURATION_TICKS;
            return new Settings(radius, duration);
        }
    }
}
