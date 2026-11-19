package at.koopro.wizardsandbeasts.client.companion.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.companion.ClientCompanionState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.ConfirmDialog;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.creature.companion.CompanionStage;
import at.koopro.wizardsandbeasts.creature.companion.PersonalityTrait;
import at.koopro.wizardsandbeasts.network.companion.CompanionActionC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The companions screen (UI renaissance P6, 2026-11-19): a keeper's cork pinboard in a walnut frame. Each companion is
 * a parchment card pinned to the cork; the chosen one stands on a portrait card on the right — the live creature, its
 * bond, health, training and temperament, where it was last seen, and the orders it will take.
 *
 * <p>A front end on the roster the server already syncs ({@link ClientCompanionState}) and the action the character
 * sheet already sends ({@link CompanionActionC2SPayload}): the server checks every order and release, as before.
 * Releasing asks first, with the kit's danger dialog (a click, never Enter).
 */
@NullMarked
public final class MenagerieScreen extends Screen {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.beasts.";
    private static final String MKEY = "gui.wizards_and_beasts.menagerie.";
    private static final UiSurface S = UiSurface.PAGE;

    static final int W = 320;
    static final int H = 220;
    private static final int BORDER = 8;
    private static final int CARD_X = 14;
    private static final int CARD_W = 100;
    private static final int CARD_H = 30;
    private static final int CARD_STEP = 34;
    private static final int CARDS_TOP = 20;
    private static final int PORTRAIT_X = 122;
    private static final int PORTRAIT_W = W - PORTRAIT_X - 12;
    private static final int BUTTON_H = 12;

    private static final Identifier FRAME = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "textures/gui/container/wardrobe/frame.png");
    private static final Identifier CORK = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "textures/gui/menagerie/cork.png");
    private static final Identifier PIN = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
            "textures/gui/menagerie/pin.png");
    private static final int BRASS = 0xFFD4A84A;
    private static final int BRASS_DARK = 0xFF7A5418;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Identifier, @Nullable LivingEntity> models = new HashMap<>();
    private final ConfirmDialog confirm = new ConfirmDialog();
    private @Nullable UUID selected;
    private int scroll;
    private int left;
    private int top;

    public MenagerieScreen() {
        super(Component.translatable(MKEY + "title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        // Ask for a fresh roster, as the sheet does: positions and health move while the screen is shut.
        ClientPacketDistributor.sendToServer(new CompanionActionC2SPayload(new UUID(0, 0),
                CompanionActionC2SPayload.Action.REFRESH, CompanionCommand.FOLLOW));
    }

    @Override
    public void render(GuiGraphics g, int realMouseX, int realMouseY, float partialTick) {
        int mouseX = confirm.isOpen() ? -1 : realMouseX;
        int mouseY = confirm.isOpen() ? -1 : realMouseY;
        hits.clear();
        super.render(g, mouseX, mouseY, partialTick);
        drawBoard(g);
        CompanionRoster roster = ClientCompanionState.get();
        List<UUID> ids = new ArrayList<>(roster.entries().keySet());
        if (ids.isEmpty()) {
            Ui.panel(g, S, left + PORTRAIT_X, top + 14, PORTRAIT_W, H - 26);
            Ui.emptyState(g, font, S, new Ui.Rect(left + PORTRAIT_X + 6, top + 20, PORTRAIT_W - 12, H - 40),
                    Component.translatable(KEY + "none"), Component.translatable(KEY + "none_hint"));
        } else {
            if (selected == null || !roster.entries().containsKey(selected)) {
                selected = ids.get(0);
            }
            drawCards(g, roster, ids, mouseX, mouseY);
            CompanionRoster.Entry entry = roster.entries().get(selected);
            if (entry != null && selected != null) {
                drawPortrait(g, selected, entry, mouseX, mouseY);
            }
        }
        confirm.render(g, font, width, height, realMouseX, realMouseY);
    }

    private void drawBoard(GuiGraphics g) {
        McStylePanel.drawNineSliceTiled(g, FRAME, left, top, W, H, 32, BORDER);
        McStylePanel.drawNineSliceTiled(g, CORK, left + BORDER, top + BORDER, W - 2 * BORDER, H - 2 * BORDER, 32, 0);
        // inner shadow where the cork meets the frame
        g.fill(left + BORDER, top + BORDER, left + W - BORDER, top + BORDER + 2, 0x50000000);
        g.fill(left + BORDER, top + BORDER, left + BORDER + 2, top + H - BORDER, 0x50000000);
        int plateW = font.width(title) + 10;
        int px = left + (W - plateW) / 2;
        g.fill(px, top + 1, px + plateW, top + 11, BRASS_DARK);
        g.fill(px + 1, top + 2, px + plateW - 1, top + 10, BRASS);
        g.drawString(font, title, px + 5, top + 2, 0xFF3A2808, false);
    }

    private int visibleCards() {
        return (H - CARDS_TOP - 12) / CARD_STEP;
    }

    private void drawCards(GuiGraphics g, CompanionRoster roster, List<UUID> ids, int mouseX, int mouseY) {
        int rows = visibleCards();
        scroll = Mth.clamp(scroll, 0, Math.max(0, ids.size() - rows));
        for (int r = 0; r < rows && scroll + r < ids.size(); r++) {
            UUID id = ids.get(scroll + r);
            CompanionRoster.Entry e = roster.entries().get(id);
            if (e == null) {
                continue;
            }
            int x = left + CARD_X;
            int y = top + CARDS_TOP + r * CARD_STEP;
            boolean chosen = id.equals(selected);
            boolean over = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            // the card's shadow on the cork, then the card
            g.fill(x + 2, y + 2, x + CARD_W + 2, y + CARD_H + 2, 0x40000000);
            McStylePanel.drawThemedSlip(g, x, y, CARD_W, CARD_H);
            if (chosen) {
                g.renderOutline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, WizardsPalette.GILT_DARK);
            } else if (over) {
                g.fill(x + 2, y + 2, x + CARD_W - 2, y + CARD_H - 2, S.hoverWash());
            }
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PIN, x + CARD_W / 2 - 3, y - 3,
                    0f, 0f, 7, 7, 7, 7);
            int ink = e.alive() ? WizardsPalette.PAGE_INK : WizardsPalette.PAGE_INK_3;
            String stage = Component.translatable(e.alive() ? e.stage().key()
                    : "companion.wizards_and_beasts.memorial").getString();
            g.drawString(font, UiText.fit(font, nameOf(e).getString(), CARD_W - 12 - font.width(stage)),
                    x + 5, y + 6, ink, false);
            g.drawString(font, stage, x + CARD_W - 5 - font.width(stage), y + 6,
                    e.alive() ? stageColour(e.stage()) : WizardsPalette.PAGE_INK_3, false);
            bar(g, x + 5, y + 19, CARD_W - 10, e.bond() / (float) Math.max(1, e.maxBond()), e.alive());
            hits.add(new Hit(x, y, CARD_W, CARD_H, () -> selected = id));
        }
        if (ids.size() > rows) {
            String more = (scroll + rows) + " / " + ids.size();
            g.drawString(font, more, left + CARD_X + CARD_W - font.width(more), top + H - 18, 0xFFF0E0C0, true);
        }
    }

    private void drawPortrait(GuiGraphics g, UUID id, CompanionRoster.Entry e, int mouseX, int mouseY) {
        int px = left + PORTRAIT_X;
        int py = top + 14;
        int ph = H - 26;
        g.fill(px + 2, py + 2, px + PORTRAIT_W + 2, py + ph + 2, 0x40000000);
        Ui.panel(g, S, px, py, PORTRAIT_W, ph);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PIN, px + PORTRAIT_W / 2 - 3, py - 3,
                0f, 0f, 7, 7, 7, 7);

        int x = px + 8;
        int y = py + 8;
        int innerW = PORTRAIT_W - 16;
        Ui.Rect frame = new Ui.Rect(x, y, 60, 76);
        LivingEntity model = model(e.species());
        if (model != null && e.alive()) {
            Ui.entityPreview(g, font, S, frame, model, null, mouseX, mouseY);
        } else {
            Ui.inset(g, S, frame.x(), frame.y(), frame.w(), frame.h());
        }

        int sx = x + 66;
        int sw = innerW - 66;
        int sy = y;
        UiText.drawFit(g, font, S, nameOf(e), sx, sy, sw, S.heading());
        sy += 10;
        Component species = Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(e.species()).getDescriptionId());
        UiText.drawFit(g, font, S, species, sx, sy, sw, S.ink3());
        sy += 10;
        Component stage = Component.translatable(e.alive() ? e.stage().key() : "companion.wizards_and_beasts.memorial");
        UiText.drawFit(g, font, S, stage, sx, sy, sw, e.alive() ? stageColour(e.stage()) : S.ink3());
        sy += 12;
        sy = Ui.labelledBar(g, font, S, sx, sy, sw, Component.translatable(KEY + "bond"),
                Component.literal(e.bond() + "/" + e.maxBond()), e.bond() / (float) Math.max(1, e.maxBond()),
                WizardsPalette.PAGE_RUBRIC);
        int target = e.stage().atLeast(CompanionStage.TRAINED) ? e.masteredAt() : e.trainedAt();
        sy = Ui.labelledBar(g, font, S, sx, sy, sw, Component.translatable(KEY + "training"),
                Component.literal(e.training() + "/" + target), e.training() / (float) Math.max(1, target),
                WizardsPalette.GILT_DARK);

        y += 82;
        if (e.alive()) {
            // Health and what it is doing share a row: two short facts, read together.
            int half = (innerW - 8) / 2;
            Ui.statRow(g, font, S, x, y, half, Component.translatable(KEY + "health"),
                    Component.literal(String.format(java.util.Locale.ROOT, "%.0f/%.0f", e.health(), e.maxHealth())));
            y = Ui.statRow(g, font, S, x + half + 8, y, innerW - half - 8, Component.translatable(KEY + "mode"),
                    Component.translatable(e.mode().key()));
        }
        Component traits = Component.translatable(KEY + "personality").append(": ").append(e.traits().isEmpty()
                ? Component.translatable(KEY + "nothing")
                : Component.literal(String.join(", ", e.traits().stream()
                        .map(t -> Component.translatable(PersonalityTrait.key(t)).getString()).toList())));
        y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, traits, x, y, innerW, 2);
        String where = e.dimension().replace("minecraft:", "") + "  " + e.x() + ", " + e.y() + ", " + e.z();
        y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.translatable(KEY + (e.alive() ? "last_seen" : "rests_at"))
                .append(": ").append(where), x, y, innerW, 2);

        if (e.alive() && !e.commands().isEmpty()) {
            // Release keeps the bottom row to itself; orders flow above it and never run into it.
            int releaseY = py + ph - BUTTON_H - 6;
            int limit = releaseY - 3;
            y += 3;
            int bx = x;
            int shown = 0;
            for (CompanionCommand command : e.commands()) {
                Component label = Component.translatable(command.key());
                int bw = font.width(label) + 8;
                if (bx + bw > x + innerW) {
                    bx = x;
                    y += BUTTON_H + 2;
                }
                if (y + BUTTON_H > limit) {
                    break;
                }
                boolean usable = e.usable().contains(command);
                boolean current = command == e.mode();
                button(g, bx, y, bw, label, usable, current ? McStylePanel.ButtonTone.SELECT : McStylePanel.ButtonTone.NEUTRAL,
                        mouseX, mouseY, () -> send(id, CompanionActionC2SPayload.Action.COMMAND, command));
                bx += bw + 3;
                shown++;
            }
            if (shown < e.commands().size()) {
                g.drawString(font, "+" + (e.commands().size() - shown), x, releaseY + 2, S.ink3(), false);
            }
            Component release = Component.translatable(KEY + "release");
            int rw = font.width(release) + 10;
            button(g, px + PORTRAIT_W - 8 - rw, releaseY, rw, release, true, McStylePanel.ButtonTone.DANGER, mouseX, mouseY,
                    () -> confirm.open(Component.translatable(MKEY + "release.title", nameOf(e)),
                            Component.translatable(MKEY + "release.body"),
                            Component.translatable(KEY + "release"), Component.translatable("gui.cancel"),
                            McStylePanel.ButtonTone.DANGER,
                            () -> send(id, CompanionActionC2SPayload.Action.RELEASE, CompanionCommand.FOLLOW)));
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean enabled, McStylePanel.ButtonTone tone,
                        int mouseX, int mouseY, Runnable action) {
        boolean over = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_H;
        McStylePanel.drawThemedButton(g, x, y, w, BUTTON_H, tone, McStylePanel.ControlState.of(enabled, over));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 2,
                enabled ? WizardsPalette.PAGE_INK : WizardsPalette.PAGE_INK_3, false);
        if (enabled) {
            hits.add(new Hit(x, y, w, BUTTON_H, action));
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float f, boolean alive) {
        g.fill(x, y, x + w, y + 4, WizardsPalette.PAGE_SHADE);
        g.fill(x, y, x + Math.round(Mth.clamp(f, 0f, 1f) * w), y + 4,
                alive ? WizardsPalette.PAGE_RUBRIC : WizardsPalette.PAGE_INK_3);
    }

    private @Nullable LivingEntity model(Identifier species) {
        return models.computeIfAbsent(species, id -> {
            if (minecraft == null || minecraft.level == null) {
                return null;
            }
            Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(minecraft.level, EntitySpawnReason.LOAD);
            return entity instanceof LivingEntity living ? living : null;
        });
    }

    private static Component nameOf(CompanionRoster.Entry e) {
        Component species = Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(e.species()).getDescriptionId());
        return e.name().isEmpty() ? species : Component.literal(e.name());
    }

    private static int stageColour(CompanionStage stage) {
        return switch (stage) {
            case WILD, CURIOUS -> WizardsPalette.PAGE_INK_3;
            case TRUSTING, BONDED -> WizardsPalette.PAGE_INK;
            case TRAINED, MASTERED -> WizardsPalette.PAGE_GOOD;
        };
    }

    private static void send(UUID id, CompanionActionC2SPayload.Action action, CompanionCommand command) {
        ClientPacketDistributor.sendToServer(new CompanionActionC2SPayload(id, action, command));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (confirm.mouseClicked(event.x(), event.y())) {
            return true;
        }
        for (Hit hit : List.copyOf(hits)) {
            if (hit.contains(event.x(), event.y())) {
                hit.action().run();
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return confirm.keyPressed(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!confirm.isOpen()) {
            scroll -= (int) Math.signum(scrollY);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        models.clear();
        super.removed();
    }
}
