package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminSectionHeader;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.client.hud.widget.HudDurationBar;
import at.koopro.wizardsandbeasts.client.hud.widget.HudStatusChip;
import at.koopro.wizardsandbeasts.client.spell.hud.SpellDiamondOverlay;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


/**
 * Visuals → HUD: the spell HUD toggle (each player's own preference) and a preview of the HUD pieces on a dark
 * backdrop, fed sample numbers on a clock of its own — the spell plate with its cooldown sweep (the live overlay's own
 * {@link SpellDiamondOverlay#renderPreview}), a cooldown bar, potion and elixir timers ({@link HudDurationBar}) and
 * status indicators ({@link HudStatusChip}). The last two are the shared pieces a live elixir / potion HUD builds on;
 * nothing of the viewer's own HUD state is read.
 */
@NullMarked
final class HudPreviewPanel implements AdminPanel {

    private static final String KEY = "admin.wizards_and_beasts.hud.";
    private static final Identifier SPELL_HUD = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "show_spell_hud_overlay");
    /** Sample timers, in ticks. */
    private static final int COOLDOWN = 60;
    private static final long POTION = 20L * 60 * 3;
    private static final long ELIXIR = 24_000L;
    private static final String[] SAMPLE_SPELLS = {"stupefy", "expelliarmus", "lumos"};

    static boolean paused;

    private final PlacedRows rows = new PlacedRows();
    private @Nullable AdminPanelHost host;
    private int x;
    private int y;
    private int w;
    private int h;
    private int canvasTop;
    private int clock;

    @Override
    public AdminCategory section() {
        return AdminCategory.VISUALS;
    }

    @Override
    public void init(AdminPanelHost host, int x, int y, int w, int h) {
        this.host = host;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rows.clear();
        Font font = host.font();
        int next = rows.add(host, font, SPELL_HUD, x, y + 32, w);
        host.addPanelWidget(new AdminButton(x, next + 2, 70, 16, Component.translatable(KEY + (paused ? "play" : "pause")),
                AdminButton.Tone.NEUTRAL, () -> {
                    paused = !paused;
                    host.requestRebuild();
                }));
        canvasTop = next + 22;
    }

    @Override
    public void tick() {
        if (!paused) {
            clock++;
        }
    }

    @Override
    public void onServerState() {
        if (host != null) {
            if (rows.isEmpty()) {
                host.requestRebuild();
            } else {
                rows.refresh(host);
            }
        }
    }

    private static @Nullable String sampleSpell() {
        for (String id : SAMPLE_SPELLS) {
            Spell spell = Spells.byId(id);
            if (spell != null) {
                return spell.getId();
            }
        }
        return Spells.all().stream().findFirst().map(Spell::getId).orElse(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (host == null) {
            return;
        }
        Font font = host.font();
        AdminSectionHeader.render(g, font, x, y, w, Component.translatable(KEY + "title"), Component.translatable(KEY + "summary"));
        rows.render(host, g, font, mouseX, mouseY);

        int noteY = canvasTop;
        for (FormattedCharSequence line : font.split(Component.translatable(KEY + "note"), w)) {
            g.drawString(font, line, x, noteY, AdminTheme.INK_3, false);
            noteY += 10;
        }
        int top = noteY + 4;
        int bottom = y + h;
        if (bottom - top < 60) {
            return;
        }
        g.fill(x, top, x + w, bottom, AdminTheme.FRAME);
        g.enableScissor(x, top, x + w, bottom);

        float cooldown = 1f - (clock % COOLDOWN) / (float) COOLDOWN;
        String spell = sampleSpell();
        int plate = 0;
        if (spell != null) {
            plate = SpellDiamondOverlay.renderPreview(g, x + w - 96 - 8, top + 16, spell, cooldown);
        }
        int colW = Math.max(80, w - plate - 28);
        int cy = top + 8;
        label(g, font, x + 8, cy, KEY + "cooldowns", colW);
        cy += 11;
        long cooldownLeft = Math.round(cooldown * COOLDOWN);
        Component spellName = spell == null ? Component.literal("—") : Component.translatable(Spells.byId(spell).getDisplayName());
        cy += HudDurationBar.render(g, font, x + 8, cy, colW, spellName, cooldownLeft, COOLDOWN, 0x8FB8FF) + 6;

        label(g, font, x + 8, cy, KEY + "durations", colW);
        cy += 11;
        long potionLeft = POTION - (clock * 20L) % POTION;
        cy += HudDurationBar.render(g, font, x + 8, cy, colW, Component.translatable(KEY + "sample.potion"), potionLeft,
                POTION, 0xE0B040) + 3;
        long elixirLeft = ELIXIR - (clock * 97L) % ELIXIR;
        cy += HudDurationBar.render(g, font, x + 8, cy, colW, Component.translatable(KEY + "sample.elixir"), elixirLeft,
                ELIXIR, 0xD04040) + 6;

        label(g, font, x + 8, cy, KEY + "status", colW);
        cy += 11;
        int chipX = x + 8;
        boolean blink = (clock / 10) % 2 == 0;
        chipX += HudStatusChip.render(g, font, chipX, cy, "✚", Component.translatable(KEY + "sample.warded"), 0x7FD7FF) + 4;
        chipX += HudStatusChip.render(g, font, chipX, cy, "☠", Component.translatable(KEY + "sample.traced"),
                blink ? 0xFF6060 : 0xA04040) + 4;
        HudStatusChip.render(g, font, chipX, cy, "☽", Component.translatable(KEY + "sample.transformed"), 0xC0A0FF);
        g.disableScissor();
    }

    private static void label(GuiGraphics g, Font font, int lx, int ly, String key, int width) {
        g.drawString(font, AdminText.clip(font, Component.translatable(key).getString(), width), lx, ly,
                AdminTheme.FRAME_TEXT_DIM, false);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (host != null) {
            rows.renderTooltip(g, host.font(), mouseX, mouseY);
        }
    }

    /** The only row is each player's own preference, which the server does not reset. */
    @Override
    public boolean hasSectionReset() {
        return false;
    }
}
