package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.education.ClientEducationState;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.education.EducationView;
import at.koopro.wizardsandbeasts.network.education.EducationActionC2SPayload;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLGradeRules;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.ProfessionEligibility;
import at.koopro.wizardsandbeasts.owl.Qualification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Education tab: qualifications sat, every examined subject's coursework against its next grade, the latest
 * sitting's marks, the chosen career's requirements and what to do next.
 *
 * <p>A readout. Everything drawn comes from {@link ClientEducationState} — the view the server built — plus the
 * synced grade sheets the career requirements are checked against with the same rule the server uses
 * ({@link ProfessionEligibility}). Sittings are opened at an examination desk, not here. Hovering a subject says what
 * counts for it and what its practical is.
 */
public final class EducationTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.education.";

    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_LABEL = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_VALUE = WizardsPalette.PAGE_INK;
    private static final int COLOR_MUTED = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_GOOD = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_BAR_TRACK = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_BAR_FILL = WizardsPalette.GILT_DARK;

    private static final int ROW_H = 9;
    private static final int BAR_H = 3;

    /** A subject's rows this frame, for the hover card. */
    private record Hover(int y0, int y1, OWLSubject subject, String practical) {}

    private final List<Hover> hovers = new ArrayList<>();
    private @Nullable List<Component> tooltip;
    private float scrollOffset = 0f;
    private int lastTotalH = 0;

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.education";
    }

    @Override
    public void onShown() {
        scrollOffset = 0f;
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(EducationActionC2SPayload.of(EducationActionC2SPayload.Action.VIEW));
        }
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        hovers.clear();
        tooltip = null;
        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        g.enableScissor(x, y, x + w, y + h);
        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        int innerW = w - 4 - TabScrollbar.WIDTH;

        EducationView view = ClientEducationState.get();
        if (!view.enabled()) {
            cy = paragraph(g, font, cx, cy, innerW, Component.translatable(KEY + "off"), COLOR_MUTED);
        } else {
            cy = summary(g, font, cx, cy, innerW, view);
            cy = subjects(g, font, cx, cy + 4, innerW, view);
            cy = results(g, font, cx, cy + 4, innerW, view);
            cy = career(g, font, cx, cy + 4, innerW);
            cy = next(g, font, cx, cy + 4, innerW, view);
        }

        g.disableScissor();
        lastTotalH = cy - top + 4;
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);

        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            for (Hover hover : hovers) {
                if (mouseY >= hover.y0() && mouseY < hover.y1()) {
                    String id = hover.subject().name().toLowerCase(Locale.ROOT);
                    tooltip = List.of(Component.translatable(hover.subject().translationKey()),
                            Component.translatable(KEY + "counts." + id),
                            Component.translatable(KEY + "practical", Component.translatable(hover.practical())));
                    break;
                }
            }
        }
    }

    private int summary(GuiGraphics g, Font font, int x, int y, int w, EducationView view) {
        g.drawString(font, Component.translatable(KEY + "title").getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        long now = gameTime();
        y = row(g, font, x, y, w, Component.translatable(KEY + "sat", Component.translatable(Qualification.OWL.translationKey())),
                Component.literal(String.valueOf(view.owlSittings())), COLOR_VALUE);
        y = row(g, font, x, y, w, Component.translatable(KEY + "sat", Component.translatable(Qualification.NEWT.translationKey())),
                Component.literal(String.valueOf(view.newtSittings())), COLOR_VALUE);
        if (view.sitting() != null) {
            EducationView.SittingView s = view.sitting();
            long done = s.papers().stream().filter(EducationView.PaperLine::done).count();
            y = paragraph(g, font, x, y + 1, w, Component.translatable(KEY + "sitting",
                    Component.translatable(s.level().translationKey()), clock(s.deadline() - now), done, s.papers().size()),
                    COLOR_GOOD);
        } else {
            y = row(g, font, x, y, w, Component.translatable(KEY + "next_owl"), when(view.maySitOwl(), view.owlNext(), now),
                    view.maySitOwl() ? COLOR_GOOD : COLOR_LABEL);
            long seats = view.subjects().stream().filter(EducationView.SubjectLine::newtSeat).count();
            Component newtWhen = seats == 0 ? Component.translatable(KEY + "no_seats")
                    : when(view.maySitNewt(), view.newtNext(), now);
            y = row(g, font, x, y, w, Component.translatable(KEY + "next_newt", seats), newtWhen,
                    view.maySitNewt() ? COLOR_GOOD : COLOR_LABEL);
        }
        return y;
    }

    private int subjects(GuiGraphics g, Font font, int x, int y, int w, EducationView view) {
        g.drawString(font, Component.translatable(KEY + "subjects").getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        for (EducationView.SubjectLine line : view.subjects()) {
            int y0 = y;
            String record = gradeText(Qualification.OWL, line.owlRecord());
            if (line.newtRecord() != null) {
                record += "  " + gradeText(Qualification.NEWT, line.newtRecord());
            }
            g.drawString(font, fit(font, Component.translatable(line.subject().translationKey()).getString(),
                    w - font.width(record) - 6), x, y, COLOR_VALUE, false);
            g.drawString(font, record, x + w - font.width(record), y,
                    line.owlRecord() != null && line.owlRecord().passing ? COLOR_GOOD : COLOR_MUTED, false);
            y += ROW_H;
            y = progress(g, font, x + 4, y, w - 4, Qualification.OWL, line.score(), line.owl(), line.projected(), line.lessons());
            if (line.newtSeat() && line.hasNewt() && line.newtProjected() != null) {
                y = progress(g, font, x + 4, y, w - 4, Qualification.NEWT, line.newtScore(), line.newt(),
                        line.newtProjected(), 0);
            } else if (line.hasNewt() && line.owlRecord() != null) {
                g.drawString(font, fit(font, Component.translatable(KEY + "seat_needs", OWLGrade.E.name()).getString(),
                        w - 4), x + 4, y, COLOR_MUTED, false);
                y += ROW_H;
            }
            hovers.add(new Hover(y0, y, line.subject(), line.practical()));
            y += 2;
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "not_examined").getString(), w),
                x, y, COLOR_MUTED, false);
        return y + ROW_H;
    }

    /** "Coursework A · 9/16 to O" over a thin bar. */
    private int progress(GuiGraphics g, Font font, int x, int y, int w, Qualification level, int score, int[] ladder,
                         OWLGrade projected, int lessons) {
        int target = 0;
        OWLGrade targetGrade = null;
        OWLGrade[] steps = {OWLGrade.A, OWLGrade.E, OWLGrade.O};
        for (int i = 0; i < 3; i++) {
            if (score < ladder[i]) {
                target = ladder[i];
                targetGrade = steps[i];
                break;
            }
        }
        String left = Component.translatable(KEY + (level == Qualification.OWL ? "coursework" : "coursework_newt"),
                Component.translatable(projected.translationKey())).getString();
        String right = targetGrade == null ? Component.translatable(KEY + "top").getString()
                : Component.translatable(KEY + "to", score, target, targetGrade.name()).getString();
        if (lessons > 0) {
            right = Component.translatable(KEY + "lessons", lessons, OWLGradeRules.LESSONS_TO_A).getString() + " · " + right;
        }
        g.drawString(font, font.plainSubstrByWidth(left, w - font.width(right) - 4), x, y, COLOR_LABEL, false);
        g.drawString(font, right, x + w - font.width(right), y, targetGrade == null ? COLOR_GOOD : COLOR_MUTED, false);
        y += ROW_H;
        bar(g, x, y, w, target <= 0 ? 1f : score / (float) target);
        return y + BAR_H + 1;
    }

    private int results(GuiGraphics g, Font font, int x, int y, int w, EducationView view) {
        if (view.lastResults().isEmpty()) {
            return y;
        }
        g.drawString(font, Component.translatable(KEY + "last_sitting",
                Component.translatable(view.lastResults().getFirst().level().translationKey())).getString(),
                x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        for (EducationView.ResultView r : view.lastResults()) {
            String mark = r.lifted()
                    ? r.coursework().name() + " → " + r.result().name()
                    : r.result().name();
            g.drawString(font, fit(font, Component.translatable(r.subject().translationKey()).getString(),
                    w - font.width(mark) - 6), x + 2, y, COLOR_VALUE, false);
            g.drawString(font, mark, x + w - font.width(mark), y, r.result().passing ? COLOR_GOOD : COLOR_MUTED, false);
            y += ROW_H;
        }
        return y;
    }

    /** The chosen career's requirements, met or not — the same rule the server checks with. */
    private int career(GuiGraphics g, Font font, int x, int y, int w) {
        Profession profession = ClientOWLCache.getProfession();
        if (profession == null) {
            return y;
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "career",
                Component.translatable(profession.translationKey())).getString(), w), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        List<ProfessionEligibility.Requirement> reqs = ProfessionEligibility.requirements(profession, ClientOWLCache.isGoblin());
        if (reqs.isEmpty()) {
            return paragraph(g, font, x + 2, y, w - 2, Component.translatable(KEY + "career_open"), COLOR_MUTED);
        }
        for (ProfessionEligibility.Requirement r : reqs) {
            boolean met = r.metBy(ClientOWLCache.getGrades(), ClientOWLCache.getNewtGrades());
            String text = (met ? "✔ " : "• ") + Component.translatable(KEY + "requirement",
                    Component.translatable(r.level().translationKey()), Component.translatable(r.subject().translationKey()),
                    Component.translatable(r.minGrade().translationKey())).getString();
            g.drawString(font, font.plainSubstrByWidth(text, w - 2), x + 2, y, met ? COLOR_GOOD : COLOR_VALUE, false);
            y += ROW_H;
        }
        return y;
    }

    private int next(GuiGraphics g, Font font, int x, int y, int w, EducationView view) {
        if (view.next().isEmpty()) {
            return y;
        }
        g.drawString(font, Component.translatable(KEY + "next").getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        for (Component line : view.next()) {
            y = paragraph(g, font, x + 2, y, w - 2, Component.literal("• ").append(line), COLOR_VALUE);
        }
        return y;
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static String gradeText(Qualification level, @Nullable OWLGrade grade) {
        String name = Component.translatable(level.translationKey() + ".short").getString();
        return name + " " + (grade == null ? "—" : grade.name());
    }

    /** The text cut to {@code w} with an ellipsis, so a cut name never reads as a grade ("…Dark A"). */
    private static String fit(Font font, String text, int w) {
        if (font.width(text) <= w) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, w - font.width("…"))) + "…";
    }

    private static int row(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int colour) {
        String v = value.getString();
        g.drawString(font, fit(font, label.getString(), w - font.width(v) - 6), x, y, COLOR_LABEL, false);
        g.drawString(font, v, x + w - font.width(v), y, colour, false);
        return y + ROW_H;
    }

    private static Component when(boolean now, long nextTick, long gameTime) {
        if (now) {
            return Component.translatable(KEY + "now");
        }
        long days = Math.max(1L, (nextTick - gameTime + 23_999L) / 24_000L);
        return Component.translatable(KEY + "in_days", days);
    }

    /** "7:42" from ticks left. */
    public static String clock(long ticksLeft) {
        long seconds = Math.max(0L, ticksLeft) / 20L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    private static int paragraph(GuiGraphics g, Font font, int x, int y, int w, Component text, int colour) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, w))) {
            g.drawString(font, line, x, y, colour, false);
            y += ROW_H;
        }
        return y;
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float fraction) {
        g.fill(x, y, x + w, y + BAR_H, COLOR_BAR_TRACK);
        int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_H, COLOR_BAR_FILL);
        }
    }

    @Override
    public @Nullable List<Component> consumeTooltip() {
        List<Component> out = tooltip;
        tooltip = null;
        return out;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }
}
