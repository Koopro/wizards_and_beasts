package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.character.SheetWand;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.wand.WandCastLines;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandEligibility;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Wand page: the wand this wizard carries, what it is made of, where it stands with them, what it does to their
 * spells, its character, its history, and its condition.
 *
 * <p>Everything is read from the stack's synced data components and the synced wood/core registries, through the
 * same classes the tooltip and the cast path use ({@link WandEligibility}, {@link WandCastLines} over
 * {@link WandStatsResolver}, {@link WandAllegianceService}) — so this page and the cast cannot disagree. The derived
 * lines are rebuilt only when the wand or its components change.
 */
@NullMarked
public final class WandTab extends ScrollingTab {

    private static final UiSurface S = UiSurface.PAGE;
    private static final String KEY = "gui.wizards_and_beasts.character_sheet.wand.";
    /** Short names for the bond states, for headlines; the tooltip keeps the sentences. */
    static final String STATE = KEY + "state.";

    /**
     * What the page derives from one wand, kept until the wand or any of its components changes — and, for the
     * parts that also read the wizard (a capstone can lift how a wand answers), for {@link #REFRESH_FRAMES} at most.
     */
    private record Derived(Component name, Component wood, Component core, List<WandCastLines.Line> effects,
                           List<Component> character, WandEligibility.Result eligibility, float power,
                           float cooldown) {}

    private static final int REFRESH_FRAMES = 20;

    private @Nullable ItemStack derivedFrom;
    private @Nullable Derived derived;
    private int framesSinceDerive;

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.wand";
    }

    @Override
    public void onShown() {
        resetScroll();
    }

    @Override
    protected int drawContent(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) {
            return y;
        }
        SheetWand.Found found = SheetWand.find(player);
        if (!found.present()) {
            y = UiText.draw(g, font, S, UiText.Role.BODY, Component.translatable(KEY + "none"), x, y);
            return UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "none.hint"),
                    x, y, w, 6);
        }
        ItemStack stack = found.stack();
        HolderLookup.Provider registries = player.level().registryAccess();
        Derived d = derive(player, stack, registries);

        // ── which wand ──
        Component where = Component.translatable(KEY + (found.where() == SheetWand.Where.HAND ? "in_hand" : "in_pack"));
        int whereW = Ui.chipWidth(font, where);
        UiText.drawFit(g, font, S, d.name(), x, y + 2, w - whereW - WizardsMetrics.SPACE_S, S.heading());
        Ui.chip(g, font, S, x + w - whereW, y, where, Ui.Tone.NEUTRAL);
        y += Ui.CHIP_H + WizardsMetrics.SPACE_S;

        // ── what it is made of ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.make"), null);
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "wood"), d.wood());
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "core"), d.core());
        Float length = WandComponents.getLength(stack);
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "length"),
                Component.literal(length == null ? "—" : String.format(Locale.ROOT, "%.1f\"", length)));
        WandFlexibility flex = WandComponents.getFlexibility(stack);
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "flexibility"),
                flex == null ? Component.literal("—") : flex.label());
        y += WizardsMetrics.SPACE_S;

        // ── where it stands with you ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.bond"), null);
        y = bond(g, font, x, y, w, player, stack, mouseX, mouseY);
        y += WizardsMetrics.SPACE_S;

        // ── what it does to your spells ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.effects"),
                Component.translatable(KEY + "section.effects.caption"));
        if (d.effects().isEmpty()) {
            y = UiText.draw(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "effects.neutral"), x, y);
        }
        for (WandCastLines.Line line : d.effects()) {
            int colour = UiContrast.readableOn(line.beneficial() ? S.good() : S.bad(), S.field());
            UiText.drawFit(g, font, S, line.text(), x, y, w, colour);
            y += WizardsMetrics.LINE_TIGHT;
        }
        y = allegianceEffect(g, font, x, y, w, stack, d);
        y += WizardsMetrics.SPACE_S;

        // ── its character ──
        if (!d.character().isEmpty()) {
            y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.character"), null);
            for (Component line : d.character()) {
                y = UiText.drawWrapped(g, font, S, UiText.Role.LABEL, line, x, y, w, 2);
            }
            y += WizardsMetrics.SPACE_S;
        }

        // ── its history ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.history"), null);
        y = history(g, font, x, y, w, player, stack);
        y += WizardsMetrics.SPACE_S;

        // ── its condition ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.condition"), null);
        float integrity = WandComponents.getIntegrity(stack);
        String condition = WandAllegianceRules.condition(integrity).name().toLowerCase(Locale.ROOT);
        y = Ui.labelledBar(g, font, S, x, y, w, Component.translatable(KEY + "integrity"),
                Component.translatable("wandcraft.tooltip.condition." + condition), integrity,
                integrity <= WandAllegianceRules.BROKEN_AT ? S.bad() : S.good());
        Component corruption = WandEligibility.corruptionLine(stack);
        if (corruption != null) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.literal(corruption.getString()), x, y, w, 2);
        }
        y += WizardsMetrics.SPACE_S;

        // ── how well it suits you ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.affinity"), null);
        AttributeInstance affinity = player.getAttribute(ModAttributes.WAND_AFFINITY);
        if (affinity != null) {
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(ModAttributes.WAND_AFFINITY.get().getDescriptionId()),
                    Component.literal(String.format(Locale.ROOT, "×%.2f", affinity.getValue())));
        }
        WandEligibility.Result result = d.eligibility();
        y = UiText.draw(g, font, S, UiText.Role.BODY, Component.translatable(
                result.eligible() ? "wandcraft.eligibility.can_use" : "wandcraft.eligibility.cannot_use"), x, y);
        if (result.reason() != null) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, result.reason(), x, y, w, 3);
        }
        return y;
    }

    /** The bond as a word, a bar with the next state marked on it, and what moves it. */
    private int bond(GuiGraphics g, Font font, int x, int y, int w, LocalPlayer player, ItemStack stack,
                     int mouseX, int mouseY) {
        if (!WandAllegianceService.answersToSomeone(stack)) {
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "bond"),
                    Component.translatable(STATE + "unchosen"), S.ink3());
            return UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "bond.unchosen"),
                    x, y, w, 3);
        }
        WandBondState state = WandAllegianceService.stateFor(player.getUUID(), stack);
        int top = y;
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "bond"), stateName(state), stateColour(state));
        if (state.isMaster()) {
            float score = WandComponents.getAllegianceScore(stack);
            Ui.bar(g, S, x, y, w, Ui.BAR_H, score, stateColour(state), nextThreshold(state));
            y += Ui.BAR_H + WizardsMetrics.SPACE_XS;
        }
        y = UiText.draw(g, font, S, UiText.Role.BODY, bondSentence(state), x, y);
        y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION,
                Component.translatable(KEY + "bond.next." + state.name().toLowerCase(Locale.ROOT)), x, y, w, 3);
        if (hovered(x, top, w, y - top, mouseX, mouseY)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable(KEY + "section.bond"));
            for (WandBondState s : WandBondState.values()) {
                Component name = stateName(s);
                tip.add(s == state ? Component.literal("▶ ").append(name) : Component.literal("   ").append(name));
            }
            tip.add(Component.translatable(KEY + "bond.how"));
            tooltip(tip);
        }
        return y;
    }

    /** What the bond itself does to power and recharge, from the same rule the cast applies. */
    private static int allegianceEffect(GuiGraphics g, Font font, int x, int y, int w, ItemStack stack, Derived d) {
        if (!WandAllegianceService.answersToSomeone(stack)) {
            return y;
        }
        float power = d.power();
        float cooldown = d.cooldown();
        if (Math.abs(power - 1.0f) > 1.0e-3f) {
            y = effect(g, font, x, y, w, Component.translatable(KEY + "bond.power", signed(power)), power > 1.0f);
        }
        if (Math.abs(cooldown - 1.0f) > 1.0e-3f) {
            y = effect(g, font, x, y, w, Component.translatable(KEY + "bond.cooldown", signed(cooldown)), cooldown < 1.0f);
        }
        return y;
    }

    private static int effect(GuiGraphics g, Font font, int x, int y, int w, Component text, boolean good) {
        UiText.drawFit(g, font, S, text, x, y, w, UiContrast.readableOn(good ? S.good() : S.bad(), S.field()));
        return y + WizardsMetrics.LINE_TIGHT;
    }

    private static int history(GuiGraphics g, Font font, int x, int y, int w, LocalPlayer player, ItemStack stack) {
        WandBondHistory history = WandComponents.getBondHistory(stack);
        UUID self = player.getUUID();
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "first_master"), who(history.firstMaster(), self));
        if (history.formerMaster() != null) {
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "former_master"),
                    who(history.formerMaster(), self));
        }
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "casts"),
                Component.literal(Integer.toString(WandComponents.getCastCount(stack))));
        if (history.challenger() != null && history.challengerWins() > 0) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.translatable(KEY + "challenged",
                    who(history.challenger(), self), history.challengerWins()), x, y, w, 2);
        }
        if (WandAllegianceService.passedOn(stack)) {
            y = UiText.draw(g, font, S, UiText.Role.CAPTION, Component.translatable("wandcraft.tooltip.bond.passed_on"), x, y);
        }
        return y;
    }

    /** "You", a name from the player list, or "another wizard" — never a raw UUID. */
    private static Component who(@Nullable UUID id, UUID self) {
        if (id == null) {
            return Component.translatable(KEY + "nobody");
        }
        if (id.equals(self)) {
            return Component.translatable(KEY + "you");
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        return info == null ? Component.translatable(KEY + "someone") : Component.literal(info.getProfile().name());
    }

    private Derived derive(LocalPlayer player, ItemStack stack, HolderLookup.Provider registries) {
        ItemStack from = derivedFrom;
        Derived cached = derived;
        if (cached != null && from != null && ++framesSinceDerive < REFRESH_FRAMES
                && ItemStack.isSameItemSameComponents(from, stack)) {
            return cached;
        }
        framesSinceDerive = 0;
        List<Component> character = new ArrayList<>();
        for (Component line : WandEligibility.characterLines(stack, registries)) {
            // The tooltip's lines carry an indent and grey italics for a dark card; the page sets its own ink.
            character.add(Component.literal(line.getString().strip()));
        }
        cached = new Derived(stack.getHoverName(), WandLoreNames.wood(registries, WandComponents.getWood(stack)),
                WandLoreNames.core(registries, WandComponents.getCore(stack)),
                WandCastLines.lines(WandStatsResolver.resolve(stack, registries)), character,
                WandEligibility.evaluate(player, stack), WandAllegianceService.powerMultiplier(player, stack, registries),
                WandAllegianceService.cooldownMultiplier(player, stack, registries));
        derived = cached;
        derivedFrom = stack.copy();
        return cached;
    }

    // ── shared with the Identity page's wand card ─────────────────────────

    static Component stateName(WandBondState state) {
        return Component.translatable(STATE + state.name().toLowerCase(Locale.ROOT));
    }

    static Component bondSentence(WandBondState state) {
        return Component.translatable("wandcraft.tooltip.bond." + state.name().toLowerCase(Locale.ROOT));
    }

    static int stateColour(WandBondState state) {
        return switch (state) {
            case UNFAMILIAR -> S.bad();
            case RELUCTANT -> S.warn();
            case ACCEPTING -> S.ink();
            case LOYAL, MASTERED -> S.good();
        };
    }

    /** Where the next state begins on the bond bar, or -1 when there is no next state to reach by casting. */
    static float nextThreshold(WandBondState state) {
        return switch (state) {
            case UNFAMILIAR, MASTERED -> -1.0f;
            case RELUCTANT -> WandAllegianceRules.RELUCTANT_BELOW;
            case ACCEPTING -> WandAllegianceRules.ACCEPTING_BELOW;
            case LOYAL -> WandAllegianceRules.LOYAL_BELOW;
        };
    }

    /** {@code 1.05f} renders as {@code +5%}; an ASCII hyphen for the minus, as {@link WandCastLines} explains. */
    private static String signed(float multiplier) {
        int percent = Math.round((multiplier - 1.0f) * 100.0f);
        return (percent >= 0 ? "+" : "-") + Math.abs(percent) + "%";
    }
}
