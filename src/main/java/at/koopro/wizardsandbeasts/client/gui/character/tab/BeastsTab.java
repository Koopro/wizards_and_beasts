package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.companion.ClientCompanionState;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.creature.companion.CompanionMemory;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.creature.companion.CompanionStage;
import at.koopro.wizardsandbeasts.creature.companion.PersonalityTrait;
import at.koopro.wizardsandbeasts.network.companion.CompanionActionC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Beasts tab: every companion the player owns — nearby, far away or remembered — and, for the one selected,
 * its bond, personality, training, abilities, family, story and the orders it takes.
 *
 * <p>Reads {@link ClientCompanionState}, which is the owner's roster as the server last sent it; every number
 * is the server's. Order buttons send intents; the server finds the creature and decides whether it obeys.
 */
public final class BeastsTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.beasts.";

    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_LABEL = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_VALUE = WizardsPalette.PAGE_INK;
    private static final int COLOR_MUTED = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_GOOD = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_TRACK = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_FILL = WizardsPalette.GILT_DARK;
    private static final int COLOR_SELECTED = 0x33A06A2C;
    private static final int COLOR_BUTTON = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_BUTTON_HOVER = WizardsPalette.GILT;
    private static final int COLOR_BUTTON_OFF = WizardsPalette.PAGE_INK_3;

    private static final int ROW_H = 9;
    private static final int BAR_H = 3;
    private static final int BUTTON_H = 11;
    private static final int HISTORY_SHOWN = 6;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private @Nullable UUID selected;
    private @Nullable UUID releaseArmed;
    private float scrollOffset;
    private int lastTotalH;
    private int clipTop;
    private int clipBottom;

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.beasts";
    }

    @Override
    public void onShown() {
        scrollOffset = 0f;
        releaseArmed = null;
        send(new UUID(0, 0), CompanionActionC2SPayload.Action.REFRESH, CompanionCommand.FOLLOW);
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        hits.clear();
        clipTop = y;
        clipBottom = y + h;
        scrollOffset = Mth.clamp(scrollOffset, 0f, Math.max(0, lastTotalH - h));
        g.enableScissor(x, y, x + w, y + h);
        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        int innerW = w - 4 - TabScrollbar.WIDTH;

        cy = journal(g, font, cx, cy, innerW);

        CompanionRoster roster = ClientCompanionState.get();
        if (roster.entries().isEmpty()) {
            cy = paragraph(g, font, cx, cy, innerW, Component.translatable(KEY + "none"), COLOR_VALUE);
            cy = paragraph(g, font, cx, cy + 2, innerW, Component.translatable(KEY + "none_hint"), COLOR_MUTED);
        } else {
            if (selected == null || !roster.entries().containsKey(selected)) {
                selected = roster.entries().keySet().iterator().next();
            }
            for (Map.Entry<UUID, CompanionRoster.Entry> e : roster.entries().entrySet()) {
                cy = row(g, font, cx, cy, innerW, e.getKey(), e.getValue(), mouseX, mouseY);
            }
            cy += 4;
            CompanionRoster.Entry entry = roster.entries().get(selected);
            if (entry != null) {
                cy = detail(g, font, cx, cy, innerW, selected, entry, mouseX, mouseY);
            }
            cy = trustLists(g, font, cx, cy + 4, innerW, roster);
        }

        g.disableScissor();
        lastTotalH = cy - top + 4;
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    /**
     * The field journal in one line: how many creatures' pages stand at each depth. The journal is the other half of
     * a wizard's life with beasts — the ones they know, not only the ones that follow them — and it is synced at
     * login, so the line needs no request.
     */
    private static int journal(GuiGraphics g, Font font, int x, int y, int w) {
        java.util.Map<at.koopro.wizardsandbeasts.bestiary.DiscoveryTier, Integer> counts =
                new java.util.EnumMap<>(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.class);
        for (at.koopro.wizardsandbeasts.bestiary.DiscoveryTier tier
                : at.koopro.wizardsandbeasts.client.bestiary.ClientBestiaryCache.get().tiers().values()) {
            counts.merge(tier, 1, Integer::sum);
        }
        g.drawString(font, Component.translatable(KEY + "journal").getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        y = paragraph(g, font, x, y, w, Component.translatable(KEY + "journal.tiers",
                counts.getOrDefault(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.KNOWN, 0),
                counts.getOrDefault(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.STUDIED, 0),
                counts.getOrDefault(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.OBSERVED, 0),
                counts.getOrDefault(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.ENCOUNTERED, 0)), COLOR_LABEL);
        g.drawString(font, Component.translatable(KEY + "companions").getString(), x, y + 3, COLOR_SECTION, false);
        return y + ROW_H + 4;
    }

    private static Component nameOf(CompanionRoster.Entry entry) {
        Component species = Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(entry.species()).getDescriptionId());
        return entry.name().isEmpty() ? species : Component.literal(entry.name());
    }

    private int row(GuiGraphics g, Font font, int x, int y, int w, UUID id, CompanionRoster.Entry entry, int mx, int my) {
        int h = ROW_H * 2;
        if (id.equals(selected)) {
            g.fill(x - 1, y - 1, x + w + 1, y + h, COLOR_SELECTED);
        }
        String stage = Component.translatable(entry.alive() ? entry.stage().key()
                : "companion.wizards_and_beasts.memorial").getString();
        g.drawString(font, font.plainSubstrByWidth(nameOf(entry).getString(), w - font.width(stage) - 4), x, y,
                entry.alive() ? COLOR_VALUE : COLOR_MUTED, false);
        g.drawString(font, stage, x + w - font.width(stage), y, entry.alive() ? stageColour(entry.stage()) : COLOR_MUTED,
                false);
        bar(g, x, y + ROW_H + 1, w, entry.bond() / (float) Math.max(1, entry.maxBond()));
        if (y >= clipTop && y + h <= clipBottom) {
            hits.add(new Hit(x, y, w, h, () -> {
                selected = id;
                releaseArmed = null;
            }));
        }
        return y + h + 3;
    }

    private int detail(GuiGraphics g, Font font, int x, int y, int w, UUID id, CompanionRoster.Entry entry, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth(nameOf(entry).getString(), w), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        y = kv(g, font, x, y, w, "bond", entry.bond() + " / " + entry.maxBond());
        if (entry.alive()) {
            y = kv(g, font, x, y, w, "health", String.format(java.util.Locale.ROOT, "%.0f / %.0f",
                    entry.health(), entry.maxHealth()));
            y = kv(g, font, x, y, w, "mode", Component.translatable(entry.mode().key()).getString());
        }
        int target = entry.stage().atLeast(CompanionStage.TRAINED) ? entry.masteredAt() : entry.trainedAt();
        y = kv(g, font, x, y, w, "training", entry.training() + " / " + target);
        bar(g, x, y, w, entry.training() / (float) Math.max(1, target));
        y += BAR_H + 3;

        y = list(g, font, x, y, w, "personality", entry.traits().stream()
                .map(t -> Component.translatable(PersonalityTrait.key(t)).getString()).toList());
        y = list(g, font, x, y, w, "abilities", entry.abilities().stream()
                .map(a -> Component.translatable(a).getString()).toList());

        String where = entry.dimension().replace("minecraft:", "") + "  " + entry.x() + ", " + entry.y() + ", " + entry.z();
        y = kv(g, font, x, y, w, entry.alive() ? "last_seen" : "rests_at", where);
        if (entry.generation() > 0) {
            y = kv(g, font, x, y, w, "lineage", Component.translatable(KEY + "generation", entry.generation(),
                    String.join(" & ", entry.parents())).getString());
        }

        if (entry.alive() && !entry.commands().isEmpty()) {
            g.drawString(font, Component.translatable(KEY + "orders").getString(), x, y, COLOR_LABEL, false);
            y += ROW_H + 1;
            int bx = x;
            for (CompanionCommand command : entry.commands()) {
                Component label = Component.translatable(command.key());
                int bw = font.width(label) + 8;
                if (bx + bw > x + w) {
                    bx = x;
                    y += BUTTON_H + 2;
                }
                boolean usable = entry.usable().contains(command);
                boolean current = command == entry.mode();
                bx = button(g, font, bx, y, label, usable, current, mx, my,
                        () -> send(id, CompanionActionC2SPayload.Action.COMMAND, command)) + 3;
            }
            y += BUTTON_H + 3;
            if (entry.usable().size() < entry.commands().size()) {
                y = paragraph(g, font, x, y, w, Component.translatable(KEY + "orders_hint"), COLOR_MUTED);
            }
            boolean armed = id.equals(releaseArmed);
            button(g, font, x, y + 1, Component.translatable(KEY + (armed ? "release_confirm" : "release")), true, armed,
                    mx, my, () -> {
                        if (id.equals(releaseArmed)) {
                            send(id, CompanionActionC2SPayload.Action.RELEASE, CompanionCommand.FOLLOW);
                            releaseArmed = null;
                        } else {
                            releaseArmed = id;
                        }
                    });
            y += BUTTON_H + 5;
        }

        if (!entry.history().isEmpty()) {
            g.drawString(font, Component.translatable(KEY + "history").getString(), x, y, COLOR_LABEL, false);
            y += ROW_H;
            List<CompanionMemory.HistoryEntry> history = entry.history();
            for (int i = history.size() - 1; i >= Math.max(0, history.size() - HISTORY_SHOWN); i--) {
                CompanionMemory.HistoryEntry note = history.get(i);
                Component arg = note.key().endsWith(".stage")
                        ? Component.translatable(stageKey(note.arg())) : Component.literal(note.arg());
                Component line = Component.translatable(KEY + "day", note.day())
                        .append(" · ").append(Component.translatable(note.key(), arg));
                y = paragraph(g, font, x + 2, y, w - 2, line, COLOR_MUTED);
            }
        }
        return y;
    }

    private static String stageKey(String name) {
        try {
            return CompanionStage.valueOf(name).key();
        } catch (IllegalArgumentException e) {
            return name;
        }
    }

    private int trustLists(GuiGraphics g, Font font, int x, int y, int w, CompanionRoster roster) {
        y = list(g, font, x, y, w, "trusted", roster.trusted().stream()
                .map(id -> roster.names().getOrDefault(id, "?")).toList());
        y = list(g, font, x, y, w, "hostile", roster.hostile().stream()
                .map(id -> roster.names().getOrDefault(id, "?")).toList());
        return paragraph(g, font, x, y, w, Component.translatable(KEY + "trust_hint"), COLOR_MUTED);
    }

    private static int kv(GuiGraphics g, Font font, int x, int y, int w, String key, String value) {
        String label = Component.translatable(KEY + key).getString() + ": ";
        g.drawString(font, label, x, y, COLOR_LABEL, false);
        g.drawString(font, font.plainSubstrByWidth(value, w - font.width(label)), x + font.width(label), y, COLOR_VALUE,
                false);
        return y + ROW_H;
    }

    private static int list(GuiGraphics g, Font font, int x, int y, int w, String key, List<String> items) {
        Component line = Component.translatable(KEY + key).append(": ").append(items.isEmpty()
                ? Component.translatable(KEY + "nothing") : Component.literal(String.join(", ", items)));
        return paragraph(g, font, x, y, w, line, COLOR_LABEL);
    }

    private int button(GuiGraphics g, Font font, int x, int y, Component label, boolean enabled, boolean current,
                       int mx, int my, Runnable action) {
        int w = font.width(label) + 8;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + BUTTON_H;
        g.fill(x, y, x + w, y + BUTTON_H, hover || current ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        g.renderOutline(x, y, w, BUTTON_H, enabled ? COLOR_LABEL : COLOR_BUTTON_OFF);
        g.drawString(font, label.getString(), x + 4, y + 2, enabled ? COLOR_VALUE : COLOR_BUTTON_OFF, false);
        if (enabled && y >= clipTop && y + BUTTON_H <= clipBottom) {
            hits.add(new Hit(x, y, w, BUTTON_H, action));
        }
        return x + w;
    }

    private static int stageColour(CompanionStage stage) {
        return switch (stage) {
            case WILD, CURIOUS -> COLOR_MUTED;
            case TRUSTING, BONDED -> COLOR_VALUE;
            case TRAINED, MASTERED -> COLOR_GOOD;
        };
    }

    private static int paragraph(GuiGraphics g, Font font, int x, int y, int w, Component text, int colour) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, w))) {
            g.drawString(font, line, x, y, colour, false);
            y += ROW_H;
        }
        return y;
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float fraction) {
        g.fill(x, y, x + w, y + BAR_H, COLOR_TRACK);
        int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_H, COLOR_FILL);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.action().run();
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.6f));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    private static void send(UUID id, CompanionActionC2SPayload.Action action, CompanionCommand command) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new CompanionActionC2SPayload(id, action, command));
        }
    }
}
