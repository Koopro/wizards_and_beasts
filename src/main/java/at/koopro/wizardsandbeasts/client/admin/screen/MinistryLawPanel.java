package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import at.koopro.wizardsandbeasts.ministry.trace.CaseRules;
import at.koopro.wizardsandbeasts.ministry.trace.TraceRules;
import at.koopro.wizardsandbeasts.ministry.trace.Wizengamot;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ministry → Law: the law as the game enforces it, read straight from the code that enforces it — offences with
 * their heat and fines (scaled by the live fine setting), the wanted bands, the Trace's channels, the case ladder,
 * and what Azkaban means today. Nothing here is a setting; the page says plainly which things the game does not do
 * (no Auror creatures, no imprisonment, no fine expiry, no random detection), so no one goes looking for a switch.
 */
@NullMarked
final class MinistryLawPanel extends AdminInfoPanel {

    private static final String KEY = "admin.wizards_and_beasts.ministry_law.";

    @Override
    public AdminCategory section() {
        return AdminCategory.MINISTRY;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        out.add(heading(KEY + "status"));
        out.add(row(Component.translatable(KEY + "module_ministry"), AdminFacts.moduleState(Module.MINISTRY)));
        out.add(row(Component.translatable(KEY + "module_gringotts"), AdminFacts.moduleState(Module.GRINGOTTS)));
        out.add(row(Component.translatable(KEY + "module_azkaban"), AdminFacts.moduleState(Module.AZKABAN)));
        out.add(note(KEY + "status_note"));

        long scale = AdminFacts.settingLong("ministry_fine_scale_percent", 100);
        out.add(heading(KEY + "offences"));
        out.add(note(KEY + "offences_note", scale));
        for (MagicalOffence offence : MagicalOffence.values()) {
            String remedy = Component.translatable(KEY + "remedy." + offence.remedy().name().toLowerCase(Locale.ROOT)).getString();
            String fine = offence.fineable() && offence.fineKnuts() > 0
                    ? " · " + AdminFacts.money(Math.max(1, offence.fineKnuts() * scale / 100)) : "";
            out.add(row(offence.displayName(), Component.translatable(KEY + "offence_value",
                    String.format(Locale.ROOT, "%.1f", offence.notoriety()), remedy + fine)));
        }

        out.add(heading(KEY + "wanted"));
        for (WantedLevel level : WantedLevel.values()) {
            out.add(row(level.displayName(), Component.translatable(KEY + "wanted_from",
                    String.format(Locale.ROOT, "%.0f", level.threshold()))));
        }
        out.add(note(KEY + "wanted_note", AdminFacts.setting("ministry_notoriety_decay_per_second", "0.05")));

        out.add(heading(KEY + "trace"));
        out.add(row(Component.translatable(KEY + "trace.underage"), AdminFacts.seconds(TraceRules.TRACE_DELAY_TICKS)));
        out.add(row(Component.translatable(KEY + "trace.official"), AdminFacts.seconds(TraceRules.OFFICIAL_DELAY_TICKS)));
        out.add(row(Component.translatable(KEY + "trace.muggle"), AdminFacts.seconds(TraceRules.MUGGLE_REPORT_DELAY_TICKS)));
        out.add(row(Component.translatable(KEY + "trace.witnesses"), Integer.toString(TraceRules.WITNESSES_TO_IDENTIFY)));
        out.add(note(KEY + "trace_note"));

        out.add(heading(KEY + "enforcement"));
        out.add(row(Component.translatable(KEY + "case.warning"),
                Component.translatable(KEY + "case.breaches", CaseRules.BREACHES_BEFORE_WARNING)));
        out.add(row(Component.translatable(KEY + "case.hearing"),
                Component.translatable(KEY + "case.breaches", CaseRules.BREACHES_BEFORE_HEARING)));
        out.add(row(Component.translatable(KEY + "case.investigation"), AdminFacts.seconds(CaseRules.INVESTIGATION_TICKS)));
        out.add(row(Component.translatable(KEY + "case.summons"), AdminFacts.seconds(CaseRules.SUMMONS_TICKS)));
        out.add(row(Component.translatable(KEY + "case.search"),
                String.format(Locale.ROOT, "%.0f", CaseRules.AUROR_SEARCH_RADIUS)));
        out.add(note(KEY + "enforcement_note"));

        out.add(heading(KEY + "azkaban"));
        out.add(row(Component.translatable(KEY + "azkaban.referral"), AdminFacts.seconds(Wizengamot.CONFISCATION_LONG_TICKS)));
        out.add(note(KEY + "azkaban_note"));

        out.add(heading(KEY + "fines"));
        out.add(note(KEY + "fines_note"));
        return out;
    }
}
