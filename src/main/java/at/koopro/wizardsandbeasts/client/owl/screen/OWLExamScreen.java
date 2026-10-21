package at.koopro.wizardsandbeasts.client.owl.screen;

import at.koopro.wizardsandbeasts.client.education.ClientEducationState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.character.tab.EducationTab;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.owl.ClientOWLCache;
import at.koopro.wizardsandbeasts.education.EducationView;
import at.koopro.wizardsandbeasts.network.education.EducationActionC2SPayload;
import at.koopro.wizardsandbeasts.network.education.EducationActionC2SPayload.Action;
import at.koopro.wizardsandbeasts.owl.Qualification;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The examination desk.
 *
 * <p>Three faces, all drawn from the server's {@link EducationView}:
 * <ul>
 *   <li><b>Desk</b> — when each level may be sat, the latest marks, and the buttons to open an O.W.L. or N.E.W.T.
 *       sitting, read the full sheet or choose a career;</li>
 *   <li><b>Sitting</b> — the clock and every paper's practical, ticked off as the server credits it. The candidate
 *       leaves to do them (a brew or a herb specimen is handed in by using it on the desk) and comes back to hand in;</li>
 *   <li><b>Paper</b> — the written questions, one at a time; the last choice hands the paper in.</li>
 * </ul>
 * Every button sends an intent; the server answers with a fresh view, and the screen re-lays itself out.
 */
public class OWLExamScreen extends ScaledParchmentScreen {

    private static final int BG_WIDTH = 280;
    private static final int BG_HEIGHT = 236;
    /** One paper's two lines in the sitting view. */
    private static final int PAPER_H = 22;

    /** The sheet's height this layout: the sitting grows to hold every paper (an O.W.L. sets nine). */
    private int bgHeight = BG_HEIGHT;
    private static final int PAD = 14;
    private static final int BUTTON_H = 20;
    private static final String KEY = "education.desk.";

    private long seenVersion = -1;
    /** Answering the paper: the question shown, and the choices so far. */
    private boolean answering;
    private final List<Integer> chosen = new ArrayList<>();

    public OWLExamScreen() {
        super(Component.translatable("owls.screen.title"));
    }

    private EducationView view() {
        return ClientEducationState.get();
    }

    private int left() {
        return width / 2 - BG_WIDTH / 2;
    }

    private int top() {
        return height / 2 - bgHeight / 2;
    }

    @Override
    protected void init() {
        super.init();
        seenVersion = ClientEducationState.version();
        EducationView.SittingView sitting = view().sitting();
        boolean showsPapers = sitting != null && !(answering && !sitting.answered() && !sitting.questions().isEmpty());
        // Title, the papers, the three-line brief and the button row.
        bgHeight = showsPapers ? Math.max(BG_HEIGHT, 30 + sitting.papers().size() * PAPER_H + 44 + BUTTON_H + PAD)
                : BG_HEIGHT;
        layout(BG_WIDTH, bgHeight);
        if (sitting == null) {
            answering = false;
            initDesk();
        } else if (answering && !sitting.answered() && !sitting.questions().isEmpty()) {
            initPaper(sitting);
        } else {
            answering = false;
            initSitting(sitting);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientEducationState.version() != seenVersion) {
            rebuildWidgets();
        }
    }

    // -- desk ----------------------------------------------------------------------------------------

    private void initDesk() {
        EducationView view = view();
        int cx = width / 2;
        int row1 = top() + bgHeight - PAD - BUTTON_H * 2 - 4;
        int row2 = row1 + BUTTON_H + 4;
        ThemedButton owl = button(cx - 130, row1, 128, Component.translatable(KEY + "begin",
                Component.translatable(Qualification.OWL.translationKey())), () -> send(Action.BEGIN_OWL));
        owl.tone(McStylePanel.ButtonTone.CONFIRM);
        owl.active = view.maySitOwl();
        ThemedButton newt = button(cx + 2, row1, 128, Component.translatable(KEY + "begin",
                Component.translatable(Qualification.NEWT.translationKey())), () -> send(Action.BEGIN_NEWT));
        newt.tone(McStylePanel.ButtonTone.CONFIRM);
        newt.active = view.maySitNewt();

        ThemedButton results = button(cx - 130, row2, 84, Component.translatable("owls.button.view_results"), () -> {
            assert minecraft != null;
            minecraft.setScreen(new OWLResultsReadOnlyScreen(ClientOWLCache.getGrades(), ClientOWLCache.getProfession()));
        });
        results.active = view.owlSittings() > 0;
        ThemedButton career = button(cx - 42, row2, 84, Component.translatable("owls.button.choose_path"), () -> {
            assert minecraft != null;
            minecraft.setScreen(new ProfessionSelectionScreen(ClientOWLCache.getGrades()));
        });
        career.active = view.careerOpen();
        button(cx + 46, row2, 84, Component.translatable(KEY + "close"), this::onClose);
    }

    private void renderDesk(GuiGraphics g) {
        EducationView view = view();
        int x = left() + PAD;
        int w = BG_WIDTH - PAD * 2;
        int y = top() + 12;
        drawCentred(g, Component.translatable("owls.screen.title"), width / 2, y, WizardsPalette.PAGE_RUBRIC);
        y += 16;
        long now = now();
        y = line(g, x, y, w, Component.translatable(KEY + "owl_status", view.owlSittings(),
                view.maySitOwl() ? Component.translatable(KEY + "now") : days(view.owlNext(), now)), WizardsPalette.PAGE_INK);
        long seats = view.subjects().stream().filter(EducationView.SubjectLine::newtSeat).count();
        y = line(g, x, y, w, seats == 0 ? Component.translatable(KEY + "newt_none")
                : Component.translatable(KEY + "newt_status", seats,
                view.maySitNewt() ? Component.translatable(KEY + "now") : days(view.newtNext(), now)), WizardsPalette.PAGE_INK);
        y += 6;
        if (view.lastResults().isEmpty()) {
            paragraph(g, x, y, w, Component.translatable(KEY + "brief"), WizardsPalette.PAGE_INK_2);
            return;
        }
        g.drawString(font, Component.translatable(KEY + "last", Component.translatable(
                view.lastResults().getFirst().level().translationKey())), x, y, WizardsPalette.PAGE_RUBRIC, false);
        y += 12;
        int col = 0;
        for (EducationView.ResultView r : view.lastResults()) {
            String mark = r.lifted() ? r.coursework().name() + "→" + r.result().name() : r.result().name();
            int cx = x + col * (w / 2);
            int cw = w / 2 - 6;
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(r.subject().translationKey()).getString(),
                    cw - font.width(mark) - 4), cx, y, WizardsPalette.PAGE_INK, false);
            g.drawString(font, mark, cx + cw - font.width(mark), y,
                    r.result().passing ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_INK_3, false);
            if (++col == 2) {
                col = 0;
                y += 11;
            }
        }
    }

    // -- sitting -------------------------------------------------------------------------------------

    private void initSitting(EducationView.SittingView sitting) {
        int cx = width / 2;
        int row = top() + bgHeight - PAD - BUTTON_H;
        boolean paper = !sitting.questions().isEmpty() && !sitting.answered();
        ThemedButton written = button(cx - 130, row, 84, Component.translatable(KEY + "paper", sitting.questions().size()),
                () -> {
                    answering = true;
                    chosen.clear();
                    rebuildWidgets();
                });
        written.active = paper;
        button(cx - 42, row, 84, Component.translatable(KEY + "hand_in"), () -> send(Action.HAND_IN))
                .tone(McStylePanel.ButtonTone.CONFIRM);
        button(cx + 46, row, 84, Component.translatable(KEY + "leave"), this::onClose);
    }

    private void renderSitting(GuiGraphics g, EducationView.SittingView sitting) {
        int x = left() + PAD;
        int w = BG_WIDTH - PAD * 2;
        int y = top() + 12;
        drawCentred(g, Component.translatable(KEY + "sitting_title", Component.translatable(sitting.level().translationKey()),
                EducationTab.clock(sitting.deadline() - now())), width / 2, y, WizardsPalette.PAGE_RUBRIC);
        y += 16;
        // Two lines a paper: the subject, then its practical in smaller ink — one line cut the practical in half.
        for (EducationView.PaperLine paper : sitting.papers()) {
            String mark = paper.done() ? "✔ " : "• ";
            g.drawString(font, font.plainSubstrByWidth(mark + Component.translatable(paper.subject().translationKey())
                    .getString(), w), x, y, paper.done() ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_INK, false);
            y += 10;
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(paper.practical()).getString(), w - 10),
                    x + 10, y, WizardsPalette.PAGE_INK_3, false);
            y += PAPER_H - 10;
        }
        y += 4;
        paragraph(g, x, y, w, Component.translatable(KEY + "sitting_brief"), WizardsPalette.PAGE_INK_3);
    }

    // -- written paper -------------------------------------------------------------------------------

    private void initPaper(EducationView.SittingView sitting) {
        int index = chosen.size();
        if (index >= sitting.questions().size()) {
            return;
        }
        EducationView.QuestionLine question = sitting.questions().get(index);
        int x = left() + PAD;
        int y = top() + bgHeight - PAD - BUTTON_H * 4 - 12;
        for (int i = 0; i < question.options().size(); i++) {
            int option = i;
            button(x, y, BG_WIDTH - PAD * 2, question.options().get(i), () -> choose(option));
            y += BUTTON_H + 4;
        }
        button(width / 2 - 42, y, 84, Component.translatable(KEY + "back"), () -> {
            answering = false;
            chosen.clear();
            rebuildWidgets();
        });
    }

    private void choose(int option) {
        chosen.add(option);
        EducationView.SittingView sitting = view().sitting();
        if (sitting != null && chosen.size() >= sitting.questions().size()) {
            ClientPacketDistributor.sendToServer(new EducationActionC2SPayload(Action.ANSWER, List.copyOf(chosen)));
            answering = false;
            chosen.clear();
        }
        rebuildWidgets();
    }

    private void renderPaper(GuiGraphics g, EducationView.SittingView sitting) {
        int index = Math.min(chosen.size(), sitting.questions().size() - 1);
        EducationView.QuestionLine question = sitting.questions().get(index);
        int x = left() + PAD;
        int w = BG_WIDTH - PAD * 2;
        int y = top() + 12;
        drawCentred(g, Component.translatable(KEY + "question", index + 1, sitting.questions().size(),
                Component.translatable(question.subject().translationKey())), width / 2, y, WizardsPalette.PAGE_RUBRIC);
        y += 18;
        paragraph(g, x, y, w, question.question(), WizardsPalette.PAGE_INK);
    }

    // -- frame ---------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground() here: the screen framework already ran it for this frame.
        beginScaledPass(graphics);
        drawParchment(graphics, BG_WIDTH, bgHeight);
        EducationView.SittingView sitting = view().sitting();
        if (sitting == null) {
            renderDesk(graphics);
        } else if (answering && !sitting.questions().isEmpty()) {
            renderPaper(graphics, sitting);
        } else {
            renderSitting(graphics, sitting);
        }
        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private ThemedButton button(int designX, int designY, int designW, Component label, Runnable action) {
        return addRenderableWidget(new ThemedButton(sx(designX), sy(designY), sw(designW), sw(BUTTON_H), label, action));
    }

    private int line(GuiGraphics g, int x, int y, int w, Component text, int colour) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), w), x, y, colour, false);
        return y + 11;
    }

    private int paragraph(GuiGraphics g, int x, int y, int w, Component text, int colour) {
        for (FormattedCharSequence seq : font.split(text, w)) {
            g.drawString(font, seq, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    private static Component days(long next, long now) {
        return Component.translatable(KEY + "in_days", Math.max(1L, (next - now + 23_999L) / 24_000L));
    }

    private long now() {
        return minecraft == null || minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    private static void send(Action action) {
        ClientPacketDistributor.sendToServer(EducationActionC2SPayload.of(action));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
