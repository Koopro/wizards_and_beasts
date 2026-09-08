package at.koopro.wizardsandbeasts.heritage.debug;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceState;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

/**
 * Every way this player might not currently be themselves.
 *
 * <p>Animagus, Polyjuice, metamorphmagus and the Obscurial's shadow form are four independent
 * switches that all end in "the player is drawn and treated as something else". They are stored in
 * two different attachments and were only ever inspectable one at a time, which is unhelpful in
 * precisely the situation that matters: two of them on at once, fighting over the same render path.
 * Listing them together makes that visible in one line.
 */
@NullMarked
public final class TransformationFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "transform";
    }

    @Override
    public String title() {
        return "Transformations";
    }

    @Override
    public String summary() {
        return "Animagus, Polyjuice, metamorph and disguise state — all of them at once.";
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerAbilityData ability = target.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
        PolyjuiceState polyjuice = target.getData(ModAttachments.POLYJUICE_STATE.get());

        int active = 0;
        if (ability.currentlyTransformed()) active++;
        if (polyjuice.isDisguised()) active++;
        if (ability.currentDisguiseFormId() != null && !ability.currentDisguiseFormId().isEmpty()) active++;
        report.row("  active disguises", active);
        if (active > 1) {
            report.warn("  more than one transformation is live — they share a render path");
        }
        if (detail == Detail.BRIEF) {
            report.flag("  transformed", ability.currentlyTransformed());
            report.flag("  polyjuiced", polyjuice.isDisguised());
            return;
        }

        report.section("  animagus");
        report.flag("    unlocked", ability.animagusUnlocked());
        report.flag("    registered with the Ministry", ability.animagusRegistered());
        report.row("    form id", ability.animagusFormId() == null
                ? "(none)" : ability.animagusFormId());
        report.flag("    transformed now", ability.currentlyTransformed());

        report.section("  polyjuice");
        report.flag("    disguised", polyjuice.isDisguised());
        report.row("    target", polyjuice.targetName().isEmpty() ? "(none)" : polyjuice.targetName());
        report.row("    target uuid", polyjuice.targetId().map(Object::toString).orElse("none"));
        report.row("    ticks remaining", polyjuice.ticksRemaining());

        report.section("  metamorphmagus");
        report.flag("    is metamorph", ability.metamorphmagus());
        report.row("    disguise form", ability.currentDisguiseFormId() == null
                ? "(none)" : ability.currentDisguiseFormId());

        report.section("  lycanthropy");
        report.flag("    wolfsbane active", ability.wolfsbaneActive());
    }
}
