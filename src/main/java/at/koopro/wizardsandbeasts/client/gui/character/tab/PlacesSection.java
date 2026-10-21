package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.career.ClientCareerState;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.location.ClientPlaceStandingState;
import at.koopro.wizardsandbeasts.location.PlaceStanding;
import at.koopro.wizardsandbeasts.network.location.PlaceStandingsC2SPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The Reputation page's "Known at" block: where the wizard stands in their profession, and at each place that keeps
 * a standing — the bank, the wandmaker, the apothecary, the shops of Knockturn Alley.
 *
 * <p>Every tier and number is the server's ({@code LocationProgression#standings}, {@code CareerView}); a place the
 * wizard has never dealt with is listed dimmed with how to begin, rather than left out, so the page also says where
 * there is standing to be had.
 */
@NullMarked
final class PlacesSection {

    private static final UiSurface S = UiSurface.PAGE;
    private static final String KEY = "gui.wizards_and_beasts.character_sheet.record.places.";

    private PlacesSection() {}

    static void request() {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new PlaceStandingsC2SPayload());
        }
    }

    /** Draws the block and returns the y below it. */
    static int draw(GuiGraphics g, Font font, int x, int y, int w) {
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "header"), null);

        CareerView career = ClientCareerState.get();
        if (career.state() == CareerView.TRACK) {
            y = Ui.statRow(g, font, S, x, y, w, career.profession(), career.rank(), S.ink());
            y = bar(g, x, y, w, career.nextXp() < 0 ? 1.0f : career.xp() / (float) Math.max(1, career.nextXp()));
        } else if (career.state() == CareerView.FLAT_CAREER) {
            y = Ui.statRow(g, font, S, x, y, w, career.profession(), Component.translatable(KEY + "flat"), S.ink2());
        }

        List<PlaceStanding> places = ClientPlaceStandingState.get();
        if (places.isEmpty()) {
            Component text = Component.translatable(KEY + (ClientPlaceStandingState.received() ? "none" : "asking"));
            return UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, text, x, y, w, 3) + WizardsMetrics.SPACE_S;
        }
        boolean anyUnknown = false;
        for (PlaceStanding place : places) {
            if (!place.dealtWith()) {
                anyUnknown = true;
            }
            int colour = !place.dealtWith() ? S.ink3() : place.illicit() ? S.warn() : S.ink();
            y = Ui.statRow(g, font, S, x, y, w, place.name(), place.tier(), colour);
            if (place.dealtWith()) {
                y = bar(g, x, y, w, place.atTop() ? 1.0f : place.rep() / (float) Math.max(1, place.nextRep()));
            }
        }
        if (anyUnknown) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "how"), x, y, w, 3);
        }
        return y + WizardsMetrics.SPACE_S;
    }

    private static int bar(GuiGraphics g, int x, int y, int w, float fraction) {
        Ui.bar(g, S, x, y, w, 3, fraction, S.warn(), -1.0f);
        return y + 3 + WizardsMetrics.SPACE_XS + 1;
    }
}
