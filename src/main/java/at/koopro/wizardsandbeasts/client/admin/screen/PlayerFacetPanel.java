package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.config.SettingKind;
import at.koopro.wizardsandbeasts.admin.player.PlayerActionLog;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.FacetView;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Item;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Option;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.PlayerRow;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminPlayerState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminConfirmDialog;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTextField;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One tab of Players: the online players on the left (searchable by name or UUID), and on the right one facet of the
 * selected player — Overview, Heritage, Skills, Spells, Effects, Ministry, Economy, Debug — or that player's action
 * log. Every value shown is what the server read when asked; every button is a request the server checks (authority,
 * target, argument, confirmation) and answers. Destructive actions ask here first and are sent confirmed.
 */
@NullMarked
final class PlayerFacetPanel extends AdminBrowserPanel<PlayerRow> {

    static final String KEY = "admin.wizards_and_beasts.players.";
    private static final int BUTTON_H = 16;
    private static final int MAX_FILTERED = 400;

    /** Shared by every tab: the player stays selected when the tab changes. */
    static @Nullable String selected;
    /** The choice in each tab's selector, and its filter text. */
    private static String choice = "";
    private static String filter = "";
    private static String coin = "galleons";
    private static String amount = "1";

    /** Null for the action log tab. */
    private final @Nullable Facet facet;
    private int builtVersion = -1;
    private @Nullable String askedFor;

    PlayerFacetPanel(@Nullable Facet facet) {
        this.facet = facet;
    }

    @Override
    public AdminCategory section() {
        return AdminCategory.PLAYERS;
    }

    @Override
    protected List<PlayerRow> entries() {
        return ClientAdminPlayerState.rows();
    }

    @Override
    protected String idOf(PlayerRow entry) {
        return entry.id().toString();
    }

    @Override
    protected Component nameOf(PlayerRow entry) {
        return Component.literal(entry.name());
    }

    @Override
    protected boolean enabledOf(PlayerRow entry) {
        return true;
    }

    @Override
    protected String badgesOf(PlayerRow entry) {
        String marks = entry.effects() > 0 ? "✦" : "";
        return "clear".equals(entry.wanted()) ? marks : marks + "⚠";
    }

    @Override
    protected List<Component> tooltipOf(PlayerRow entry) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entry.name()));
        lines.add(Component.literal(entry.id().toString()));
        lines.add(Component.literal(subtitleOf(entry)));
        if (!entry.location().isEmpty()) {
            lines.add(Component.literal(entry.location()));
        }
        return lines;
    }

    @Override
    protected @Nullable String selected() {
        return selected;
    }

    @Override
    protected void setSelected(String id) {
        selected = id;
        choice = "";
        filter = "";
    }

    @Override
    protected boolean stale() {
        return ClientAdminPlayerState.stale();
    }

    @Override
    protected void request() {
        AdminClientRequests.playerSearch();
    }

    @Override
    protected Component pickPrompt() {
        return Component.translatable(KEY + (ClientAdminPlayerState.loaded() && ClientAdminPlayerState.rows().isEmpty()
                ? "nobody" : "pick"));
    }

    @Override
    protected String subtitleOf(PlayerRow entry) {
        String heritage = entry.heritage().isEmpty() ? Component.translatable(KEY + "no_heritage").getString()
                : entry.heritage() + (entry.lineage().isEmpty() ? "" : " · " + entry.lineage());
        return heritage + String.format(Locale.ROOT, " · ♥ %.0f/%.0f · %s", entry.health(), entry.maxHealth(),
                entry.gameMode());
    }

    @Override
    public void onServerState() {
        super.onServerState();
        if (host != null && builtVersion != ClientAdminPlayerState.version()) {
            host.requestRebuild();
        }
    }

    // ── the page ──

    @Override
    protected int buildPage(AdminPanelHost host, Font font, PlayerRow entry, int doc, int rowW) {
        builtVersion = ClientAdminPlayerState.version();
        UUID player = entry.id();
        String asking = player + "/" + (facet == null ? "log" : facet.id()) + "/" + ClientAdminPlayerState.lastActionAt();
        if (facet == null) {
            if (ClientAdminPlayerState.log(player) == null && !asking.equals(askedFor)) {
                askedFor = asking;
                AdminClientRequests.playerLog(player);
            }
            return logPage(font, player, doc, rowW);
        }
        AdminPlayerPayloads.FacetReply reply = ClientAdminPlayerState.facet(player, facet);
        if (reply == null) {
            if (!asking.equals(askedFor)) {
                askedFor = asking;
                AdminClientRequests.playerFacet(player, facet);
            }
            return wrap(font, doc, rowW, Component.translatable("admin.wizards_and_beasts.status.loading"), AdminTheme.INK_3);
        }
        if (!reply.online()) {
            return wrap(font, doc, rowW, Component.translatable(KEY + "offline"), AdminTheme.BAD);
        }
        FacetView view = reply.view();
        doc = status(font, player, doc, rowW);
        doc = header(doc, Component.translatable(KEY + "facet." + facet.id()));
        for (AdminSpellFact fact : view.facts()) {
            Component value = fact.valueTranslatable() ? Component.translatable(fact.value()) : Component.literal(fact.value());
            doc = wrap(font, doc, rowW, Component.translatable(fact.labelKey()).append(": ").append(value), AdminTheme.INK_2);
        }
        return switch (facet) {
            case OVERVIEW -> items(host, font, view, doc, rowW, "wands", null);
            case HERITAGE -> heritageActions(host, font, entry, view, doc, rowW);
            // Actions above the lists: a list can run to a hundred lines (every spell known).
            case SKILLS -> items(host, font, view, skillActions(host, font, entry, doc, rowW), rowW, "skills", null);
            case SPELLS -> items(host, font, view, spellActions(host, font, entry, view, doc, rowW), rowW, "known",
                    PlayerAdminAction.SPELL_REVOKE);
            case EFFECTS -> items(host, font, view, effectActions(host, font, entry, view, doc, rowW), rowW, "active",
                    PlayerAdminAction.EFFECT_REMOVE);
            case MINISTRY -> items(host, font, view, ministryActions(host, font, entry, doc, rowW), rowW, "offences", null);
            case ECONOMY -> economyActions(host, font, entry, doc, rowW);
            case DEBUG -> items(host, font, view, doc, rowW, "rejects", null);
        };
    }

    /** The facet's list, each line with its per-item action when the server marked it actionable. */
    private int items(AdminPanelHost host, Font font, FacetView view, int doc, int rowW, String heading,
                      @Nullable PlayerAdminAction action) {
        doc = header(doc + 4, Component.translatable(KEY + "list." + heading, view.items().size()));
        if (view.items().isEmpty()) {
            return wrap(font, doc, rowW, Component.translatable(KEY + "none"), AdminTheme.INK_3);
        }
        int buttonW = 56;
        for (Item item : view.items()) {
            Component label = item.labelKey() ? Component.translatable(item.label()) : Component.literal(item.label());
            Component line = Component.literal("• ").append(label).append(": " + item.value());
            if (action != null && item.actionable()) {
                place(button(host, "item." + action.id(), buttonW, AdminButton.Tone.NEUTRAL, true,
                        () -> act(host, view.player(), action, item.id(), 0, label)), rowW - buttonW, doc);
                int end = wrap(font, doc + 3, rowW - buttonW - AdminTheme.GAP, line, AdminTheme.INK);
                doc = Math.max(end, doc + BUTTON_H + 2);
            } else {
                doc = wrap(font, doc, rowW, line, AdminTheme.INK);
            }
        }
        return doc;
    }

    private int heritageActions(AdminPanelHost host, Font font, PlayerRow entry, FacetView view, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "actions"));
        doc = chooser(host, font, view.options(), doc, rowW);
        int half = (rowW - AdminTheme.GAP) / 2;
        place(button(host, "heritage_assign", half, AdminButton.Tone.PRIMARY, !view.options().isEmpty(),
                () -> withChoice(view.options(), pick -> act(host, entry.id(), PlayerAdminAction.HERITAGE_ASSIGN, pick, 0,
                        optionLabel(view.options(), pick)))), 0, doc);
        place(button(host, "heritage_reset", half, AdminButton.Tone.DANGER, true,
                () -> act(host, entry.id(), PlayerAdminAction.HERITAGE_RESET, "", 0, Component.literal(entry.name()))),
                half + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "heritage_note"), AdminTheme.INK_3);
    }

    private int skillActions(AdminPanelHost host, Font font, PlayerRow entry, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "actions"));
        int quarter = Math.max(40, (rowW - 3 * AdminTheme.GAP) / 4);
        int x = 0;
        for (int points : new int[]{1, 5, 10}) {
            place(new AdminButton(0, 0, quarter, BUTTON_H, Component.translatable(KEY + "button.add_points", points),
                    AdminButton.Tone.NEUTRAL, () -> AdminClientRequests.playerAction(entry.id(),
                    PlayerAdminAction.SKILL_POINTS_ADD, "", points)), x, doc);
            x += quarter + AdminTheme.GAP;
        }
        place(button(host, "skill_reset", quarter, AdminButton.Tone.DANGER, true,
                () -> act(host, entry.id(), PlayerAdminAction.SKILL_RESET, "", 0, Component.literal(entry.name()))), x, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "skills_note"), AdminTheme.INK_3);
    }

    private int spellActions(AdminPanelHost host, Font font, PlayerRow entry, FacetView view, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "unlock"));
        doc = chooser(host, font, view.options(), doc, rowW);
        int third = Math.max(40, (rowW - 2 * AdminTheme.GAP) / 3);
        boolean any = !view.options().isEmpty();
        place(button(host, "spell_unlock", third, AdminButton.Tone.PRIMARY, any,
                () -> withChoice(view.options(), pick -> AdminClientRequests.playerAction(entry.id(),
                        PlayerAdminAction.SPELL_UNLOCK, pick, 0))), 0, doc);
        place(button(host, "spell_grant", third, AdminButton.Tone.NEUTRAL, any,
                () -> withChoice(view.options(), pick -> act(host, entry.id(), PlayerAdminAction.SPELL_GRANT, pick, 0,
                        optionLabel(view.options(), pick)))), third + AdminTheme.GAP, doc);
        place(button(host, "spell_reset", third, AdminButton.Tone.DANGER, true,
                () -> act(host, entry.id(), PlayerAdminAction.SPELL_RESET_PROGRESS, "", 0, Component.literal(entry.name()))),
                2 * (third + AdminTheme.GAP), doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "spells_note"), AdminTheme.INK_3);
    }

    private int effectActions(AdminPanelHost host, Font font, PlayerRow entry, FacetView view, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "actions"));
        doc = chooser(host, font, view.options(), doc, rowW);
        int half = (rowW - AdminTheme.GAP) / 2;
        place(button(host, "effect_preview", half, AdminButton.Tone.NEUTRAL, !view.options().isEmpty(),
                () -> withChoice(view.options(), pick -> AdminClientRequests.playerAction(entry.id(),
                        PlayerAdminAction.EFFECT_PREVIEW, pick, 0))), 0, doc);
        place(button(host, "effects_clear", half, AdminButton.Tone.DANGER, !view.items().isEmpty(),
                () -> act(host, entry.id(), PlayerAdminAction.EFFECTS_CLEAR, "", 0, Component.literal(entry.name()))),
                half + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "effects_note"), AdminTheme.INK_3);
    }

    private int ministryActions(AdminPanelHost host, Font font, PlayerRow entry, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "actions"));
        int half = (rowW - AdminTheme.GAP) / 2;
        place(button(host, "ministry_pardon", half, AdminButton.Tone.DANGER, true,
                () -> act(host, entry.id(), PlayerAdminAction.MINISTRY_PARDON, "", 0, Component.literal(entry.name()))), 0, doc);
        place(button(host, "ministry_waive", half, AdminButton.Tone.NEUTRAL, true,
                () -> act(host, entry.id(), PlayerAdminAction.MINISTRY_WAIVE_FINE, "", 0, Component.literal(entry.name()))),
                half + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "ministry_note"), AdminTheme.INK_3);
    }

    private int economyActions(AdminPanelHost host, Font font, PlayerRow entry, int doc, int rowW) {
        doc = header(doc + 4, Component.translatable(KEY + "money"));
        if (!ClientAdminPlayerState.mayChangeMoney()) {
            return wrap(font, doc, rowW, Component.translatable(KEY + "money_locked"), AdminTheme.INK_3);
        }
        doc = wrap(font, doc, rowW, Component.translatable(KEY + "money_warning"), AdminTheme.BAD) + 2;
        int third = Math.max(40, (rowW - 2 * AdminTheme.GAP) / 3);
        place(new AdminEnumSelector(0, 0, third, BUTTON_H, List.of("galleons", "sickles", "knuts"), coin,
                value -> coin = value, value -> Component.translatable(KEY + "coin." + value)), 0, doc);
        AdminTextField field = new AdminTextField(font, 0, 0, third, BUTTON_H, SettingKind.INTEGER, 6, amount,
                text -> amount = text);
        place(field, third + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        place(button(host, "money_deposit", third, AdminButton.Tone.DANGER, true,
                () -> money(host, entry, PlayerAdminAction.MONEY_DEPOSIT)), 0, doc);
        place(button(host, "money_withdraw", third, AdminButton.Tone.DANGER, true,
                () -> money(host, entry, PlayerAdminAction.MONEY_WITHDRAW)), third + AdminTheme.GAP, doc);
        doc += BUTTON_H + AdminTheme.GAP;
        return wrap(font, doc, rowW, Component.translatable(KEY + "money_note", PlayerAdminService.MAX_COINS), AdminTheme.INK_3);
    }

    private void money(AdminPanelHost host, PlayerRow entry, PlayerAdminAction action) {
        long parsed;
        try {
            parsed = Long.parseLong(amount.trim());
        } catch (NumberFormatException e) {
            parsed = 0;
        }
        // Sent as typed: the server bounds it and says no to anything outside 1..MAX_COINS.
        act(host, entry.id(), action, coin, parsed,
                Component.literal(parsed + " ").append(Component.translatable(KEY + "coin." + coin)));
    }

    private int logPage(Font font, UUID player, int doc, int rowW) {
        doc = header(doc, Component.translatable(KEY + "log"));
        if (ClientAdminPlayerState.log(player) == null) {
            return wrap(font, doc, rowW, Component.translatable("admin.wizards_and_beasts.status.loading"), AdminTheme.INK_3);
        }
        List<PlayerActionLog.Entry> entries = ClientAdminPlayerState.logEntries(player);
        if (entries.isEmpty()) {
            return wrap(font, doc, rowW, Component.translatable(KEY + "log_empty"), AdminTheme.INK_3);
        }
        for (PlayerActionLog.Entry entry : entries) {
            String argument = entry.argument().isEmpty() ? "" : " (" + entry.argument() + ")";
            doc = wrap(font, doc, rowW, Component.literal(ProfileText.time(entry.timeMillis()) + " · " + entry.adminName()
                    + " · " + actionName(entry.action()).getString() + argument), AdminTheme.INK);
            doc = wrap(font, doc, rowW, Component.literal("   " + (entry.ok() ? "✔ " : "✖ ") + entry.result()
                    + (entry.detail().isEmpty() ? "" : " — " + entry.detail())), entry.ok() ? AdminTheme.GOOD : AdminTheme.BAD);
        }
        return wrap(font, doc + 4, rowW, Component.translatable(KEY + "log_note"), AdminTheme.INK_3);
    }

    // ── shared pieces ──

    /**
     * A filter field and a selector over the options that match it. Typing narrows the selector in place (no page
     * rebuild, so the field keeps focus); the buttons read the choice when clicked.
     */
    private int chooser(AdminPanelHost host, Font font, List<Option> options, int doc, int rowW) {
        if (options.isEmpty()) {
            return wrap(font, doc, rowW, Component.translatable(KEY + "nothing_to_choose"), AdminTheme.INK_3);
        }
        int half = (rowW - AdminTheme.GAP) / 2;
        AdminEnumSelector selector = new AdminEnumSelector(0, 0, half, BUTTON_H, filtered(options), currentChoice(options),
                value -> choice = value, id -> id.isEmpty() ? Component.translatable("admin.wizards_and_beasts.filter.empty")
                : optionLabel(options, id));
        AdminTextField field = new AdminTextField(font, 0, 0, half, BUTTON_H, SettingKind.STRING, 32, filter, text -> {
            filter = text;
            selector.setOptions(filtered(options), currentChoice(options));
            choice = currentChoice(options);
        });
        field.inkHint(Component.translatable(KEY + "filter_hint"));
        place(field, 0, doc);
        place(selector, half + AdminTheme.GAP, doc);
        return doc + BUTTON_H + AdminTheme.GAP;
    }

    private static List<String> filtered(List<Option> options) {
        String needle = filter.trim().toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        for (Option option : options) {
            String label = option.labelKey() ? I18n.get(option.label()) : option.label();
            if (needle.isEmpty() || option.id().contains(needle) || label.toLowerCase(Locale.ROOT).contains(needle)) {
                ids.add(option.id());
                if (ids.size() >= MAX_FILTERED) {
                    break;
                }
            }
        }
        return ids;
    }

    /** Runs {@code then} with the current choice, when the filter leaves one. */
    private static void withChoice(List<Option> options, java.util.function.Consumer<String> then) {
        String pick = currentChoice(options);
        if (!pick.isEmpty()) {
            then.accept(pick);
        }
    }

    private static String currentChoice(List<Option> options) {
        List<String> ids = filtered(options);
        if (ids.isEmpty()) {
            return "";
        }
        return ids.contains(choice) ? choice : ids.get(0);
    }

    private static Component optionLabel(List<Option> options, String id) {
        for (Option option : options) {
            if (option.id().equals(id)) {
                return option.labelKey() ? Component.translatable(option.label()) : Component.literal(option.label());
            }
        }
        return Component.literal(id);
    }

    private static Component actionName(String actionId) {
        String key = KEY + "action." + actionId;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(actionId);
    }

    private AdminButton button(AdminPanelHost host, String key, int w, AdminButton.Tone tone, boolean active, Runnable action) {
        AdminButton button = new AdminButton(0, 0, w, BUTTON_H, Component.translatable(KEY + "button." + key), tone, action);
        button.active = active;
        return button;
    }

    /** Sends {@code action}; a destructive one asks first, naming the player, the action and what it acts on. */
    private void act(AdminPanelHost host, UUID player, PlayerAdminAction action, String argument, long amount,
                     Component subject) {
        if (!action.destructive()) {
            AdminClientRequests.playerAction(player, action, argument, amount);
            return;
        }
        PlayerRow row = ClientAdminPlayerState.row(player);
        String name = row == null ? player.toString() : row.name();
        host.openDialog(new AdminConfirmDialog(Component.translatable(KEY + "confirm.title", actionName(action.id())),
                List.of(Component.translatable(KEY + "confirm.body", actionName(action.id()), subject, name),
                        Component.translatable(KEY + "confirm." + action.id())),
                actionName(action.id()), true,
                () -> {
                    host.closeDialog();
                    AdminClientRequests.playerAction(player, action, argument, amount);
                },
                host::closeDialog));
    }

    /** The last action on this player, as the server answered it. */
    private int status(Font font, UUID player, int doc, int rowW) {
        AdminPlayerPayloads.ActionReply last = ClientAdminPlayerState.lastAction();
        if (last == null || !last.player().equals(player) && last.action() != PlayerAdminAction.EFFECT_PREVIEW) {
            return doc;
        }
        Component text = last.success()
                ? Component.translatable(KEY + "done", actionName(last.action().id()), last.detail())
                : Component.translatable(KEY + "refused", actionName(last.action().id()), outcome(last.code()), last.detail());
        return wrap(font, doc, rowW, text, last.success() ? AdminTheme.GOOD : AdminTheme.BAD) + 2;
    }

    private static Component outcome(String code) {
        String key = KEY + "outcome." + code;
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(code);
    }

    @Override
    protected boolean loaded() {
        return ClientAdminPlayerState.loaded();
    }

    @Override
    protected Component emptyMessage() {
        return Component.translatable("admin.wizards_and_beasts.empty.players");
    }
}
