package at.koopro.wizardsandbeasts.client.gui.character.widget;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSignatureSpellState;
import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTraits;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Renders the heritage block at the top of the sheet's Identity page: who this character is.
 *
 * <p>Heritage, lineage, any condition they carry, and then their traits, magical affinities and special
 * characteristics by name ({@link HeritageTraits}). The block used to print heritage, variant, patronus and animagus
 * and stop there, which told a player their label and nothing about what it meant.
 *
 * <p>Drawn with the shared kit ({@code UiText}) since 2026-10-07. That pass removed a heading that repeated
 * the first row's label ("Heritage" over "Heritage"), English labels typed into the code, a trait heading
 * that ran into the next column, and an Animagus row that printed the internal id of whatever form the
 * player stood in ("Human default").
 */
public final class HeritageBlockWidget {

    private static final UiSurface S = UiSurface.PAGE;
    private static final int LINE_H = WizardsMetrics.LINE_TIGHT;
    private static final String NONE = "—";
    private static final String KEY = "gui.wizards_and_beasts.character_sheet.identity.";

    private HeritageBlockWidget() {}

    /**
     * Draws the heritage block as name/value rows across the page, then each trait group as a wrapped line.
     *
     * <p>Until 2026-10-15 this sat in a 92px column beside the tab content and stacked every label above its
     * value to make room. On the Identity page it has the full width, so a row is one line again.
     *
     * @return the y below the block
     */
    public static int draw(@NonNull GuiGraphics g, int x, int y, int w) {
        Font font = Minecraft.getInstance().font;
        PlayerHeritageData data = ClientHeritageDataState.get();
        @Nullable Heritage heritage = data.getSelectedHeritage();
        @Nullable HeritageVariant variant = data.getSelectedHeritageVariant();
        ConditionOrigin condition = data.getCondition();

        y = row(g, font, x, y, w, "heritage", heritage != null ? heritage.getDisplayName() : NONE);
        y = row(g, font, x, y, w, "lineage", variant != null ? variant.getDisplayName() : NONE);
        if (condition != null) {
            // Named, not hidden: the character knows. A condition is a fact about them, like a scar.
            y = row(g, font, x, y, w, "condition",
                    Component.translatable(condition.condition().getTranslationKey()).getString()
                            + " (" + Component.translatable(condition.getTranslationKey()).getString() + ")");
        }
        y = row(g, font, x, y, w, "patronus", resolvePatronus());
        String animagus = resolveAnimagus();
        if (!NONE.equals(animagus)) {
            y = row(g, font, x, y, w, "animagus", animagus);
        }

        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            java.util.List<HeritageTraits.Trait> traits = HeritageTraits.of(data, kind);
            if (traits.isEmpty()) {
                continue;
            }
            UiText.drawFit(g, font, S, Component.translatable(kind.getHeadingKey()), x, y, w, S.ink2());
            y += LINE_H;
            StringBuilder names = new StringBuilder();
            for (HeritageTraits.Trait trait : traits) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(Component.translatable(trait.getNameKey()).getString());
            }
            y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.literal(names.toString()), x + 4, y, w - 4, 4);
            y += WizardsMetrics.SPACE_XS;
        }
        return y;
    }

    // ── internals ───────────────────────────────────────────────────────────

    /** Label left, value right — the value dimmed when there is nothing to say. */
    private static int row(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                           @NonNull String key, @NonNull String value) {
        return Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + key), Component.literal(value),
                NONE.equals(value) ? S.ink3() : S.ink());
    }

    /** The patronus's animal by its entity name when the game has one, else its id tidied into words. */
    @NonNull
    private static String resolvePatronus() {
        String form = ClientSignatureSpellState.getPatronusForm();
        if (form == null || form.isEmpty()) {
            return NONE;
        }
        int colon = form.indexOf(':');
        String ns = colon >= 0 ? form.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? form.substring(colon + 1) : form;
        String key = "entity." + ns + "." + path;
        return I18n.exists(key) ? I18n.get(key) : words(path);
    }

    /**
     * The Animagus form, only when the player is in one: the human form a player stands in the rest of the
     * time is not an Animagus form, and printing its id read as a fault.
     */
    @NonNull
    private static String resolveAnimagus() {
        String formId = ClientHeritageDataState.get().getActiveFormId();
        if (formId == null || formId.isEmpty() || !formId.startsWith("animagus_")) {
            return NONE;
        }
        PlayerForm form = FormRegistry.get(formId);
        if (form == null) {
            return words(formId.substring("animagus_".length()));
        }
        // "Animagus (Stag)" under the label "Animagus" says it twice; keep the animal.
        String name = form.displayName();
        int open = name.indexOf('(');
        int close = name.lastIndexOf(')');
        return open >= 0 && close > open ? name.substring(open + 1, close) : name;
    }

    private static String words(String id) {
        String spaced = id.replace('_', ' ');
        return spaced.isEmpty() ? NONE : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
