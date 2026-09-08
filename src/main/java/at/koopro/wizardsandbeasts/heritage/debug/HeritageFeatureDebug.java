package at.koopro.wizardsandbeasts.heritage.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.form.SizeProfileRegistry;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What the player is, and what is drawing them.
 *
 * <p>Heritage is the mod's front door and the thing most likely to be in a state nobody meant: the
 * selection ceremony is a first-join gate, and a player who slipped past it has an empty heritage
 * that every downstream system then quietly treats as "no bonuses". The
 * {@code HeritageAPI}-versus-attachment cross-check is here for exactly that — the two disagreeing
 * is a specific bug, and it is invisible from anywhere else.
 */
@NullMarked
public final class HeritageFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "heritage";
    }

    @Override
    public String title() {
        return "Heritage";
    }

    @Override
    public String summary() {
        return "Heritage, variant, active form and the size profile drawing it.";
    }

    @Override
    public @Nullable Module module() {
        return Module.HERITAGE;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerHeritageData data = target.getData(ModAttachments.HERITAGE_DATA.get());

        report.row("  heritage", String.valueOf(data.getSelectedHeritage()));
        report.row("  variant", String.valueOf(data.getSelectedHeritageVariant()));
        report.row("  active form", data.getActiveFormId() == null ? "(none)" : data.getActiveFormId());

        // Two sources for one fact. They are written by different code paths and a mismatch means the
        // gate let someone through, which reads downstream as "chose nothing" rather than as an error.
        boolean apiSelected = HeritageAPI.hasHeritageSelected(target);
        if (apiSelected != data.hasHeritageSelected()) {
            report.warn("  MISMATCH — HeritageAPI says " + apiSelected
                    + ", attachment says " + data.hasHeritageSelected());
        }
        if (detail == Detail.BRIEF) {
            return;
        }

        report.flag("  selected", data.hasHeritageSelected());
        report.flag("  locked", data.isLocked());
        report.row("  transformation", String.valueOf(data.getTransformationState()));
        report.flag("  form debug overlay", data.isDebugOverlay());

        report.row("  profession", data.getSelectedProfessionId() == null
                ? "(none)" : data.getSelectedProfessionId());
        report.row("  profession points", data.getProfessionPoints()
                + " (earned " + data.getTotalProfessionPointsEarned() + ")");
        report.row("  professions unlocked", data.getUnlockedProfessions().size());

        appendForm(report, data.getActiveFormId());

        Map<String, String> flags = data.getCustomFlags();
        report.row("  custom flags", flags.isEmpty() ? "none" : String.valueOf(flags.size()));
        for (Map.Entry<String, String> flag : flags.entrySet()) {
            report.row("    " + flag.getKey(), flag.getValue());
        }
    }

    /**
     * The form the active id resolves to, and the size profile that decides the hitbox.
     *
     * <p>An id that does not resolve is worth a warning of its own: the player is rendered as
     * themselves and every size-derived number silently falls back to human, which looks like
     * nothing happening rather than like a missing form.
     */
    private static void appendForm(DebugReport report, @Nullable String formId) {
        if (formId == null) {
            return;
        }
        PlayerForm form = FormRegistry.get(formId);
        if (form == null) {
            report.warn("  form id '" + formId + "' does not resolve — drawn as the player");
            return;
        }
        report.state("  form model", String.valueOf(form.modelType()), ChatPalette.ACCENT);
        report.row("  form texture", form.texturePath().toString());
        report.row("  render flags", form.renderFlags().isEmpty()
                ? "none" : form.renderFlags().toString());

        // The size profile is named by id, not held, so a form can name one that no longer exists.
        // That resolves to the human default at render time and is invisible unless it is said here.
        SizeProfile size = SizeProfileRegistry.get(form.sizeProfileId());
        if (size == null) {
            report.warn("  size profile '" + form.sizeProfileId() + "' does not resolve");
            return;
        }
        report.row("  hitbox", String.format("%.2fw x %.2fh",
                size.hitboxWidth(), size.hitboxHeight()));
        report.row("  model scale", String.format("%.2f", size.modelScale()));
        report.row("  reach / step", String.format("+%.1f / +%.1f",
                size.reachBonus(), size.stepHeight()));
    }
}
