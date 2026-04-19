package at.koopro.neo.client.gui;

import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.network.ClientSpellDataHolder;
import at.koopro.neo.network.SpellAssignC2SPacket;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.SpellCategory;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.util.MathUtils;
import at.koopro.neo.util.TextUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SpellMenuScreen extends Screen {

    private static final int PANEL_W = 310;
    private static final int PANEL_H = 250;
    private static final int LEFT_W = 160;
    private static final int MAX_VISIBLE = 11;

    @Nullable
    private String selectedSpellId = null;
    private final List<SpellEntry> spellEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private String searchQuery = "";

    public SpellMenuScreen() {
        super(Component.literal("Spell Menu"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildSpellList();
        rebuildSlotButtons();
    }

    private void rebuildSpellList() {
        spellEntries.clear();
        PlayerSpellData data = ClientSpellDataHolder.get();

        String q = searchQuery == null ? "" : searchQuery.toLowerCase();

        for (SpellCategory category : SpellCategory.values()) {
            boolean hasSpells = false;
            for (Spell spell : Spells.all()) {
                if (spell.getCategory() == category && data.knowsSpell(spell.getId())) {
                    boolean matches = q.isEmpty()
                            || spell.getDisplayName().toLowerCase().contains(q)
                            || spell.getId().toLowerCase().contains(q);
                    if (!matches) {
                        continue;
                    }
                    if (!hasSpells) {
                        spellEntries.add(new SpellEntry(null, category));
                        hasSpells = true;
                    }
                    spellEntries.add(new SpellEntry(spell, category));
                }
            }
        }

        int maxScroll = Math.max(0, spellEntries.size() - MAX_VISIBLE);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void rebuildSlotButtons() {
        clearWidgets();

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;

        // Search box above the spell list
        int listX = panelX + 4;
        int searchY = panelY + 4;
        EditBox searchBox = new EditBox(this.font, listX, searchY, LEFT_W - 8, 14,
                Component.literal("Search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(text -> {
            searchQuery = text.toLowerCase();
            scrollOffset = 0;
            rebuildSpellList();
            rebuildSlotButtons();
        });
        addRenderableWidget(searchBox);

        int cx = panelX + LEFT_W + 75;
        int cy = panelY + 80;
        int btnSize = 32;
        int spacing = 38;

        int[][] offsets = {
                {0, -spacing},
                {spacing, 0},
                {0, spacing},
                {-spacing, 0}
        };
        String[] labels = {"Up", "Right", "Down", "Left"};

        PlayerSpellData data = ClientSpellDataHolder.get();
        for (int i = 0; i < 4; i++) {
            int slotX = cx + offsets[i][0] - btnSize / 2;
            int slotY = cy + offsets[i][1] - btnSize / 2;
            final int slotIdx = i;

            String spellId = data.getLoadoutSpell(i);
            String label = labels[i];
            if (spellId != null) {
                Spell spell = Spells.byId(spellId);
                if (spell != null) {
                    label = TextUtils.abbreviate(spell.getDisplayName(), 5);
                }
            }

            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                if (selectedSpellId != null) {
                    ClientSpellDataHolder.get().setLoadoutSpell(slotIdx, selectedSpellId);
                    ClientPacketDistributor.sendToServer(new SpellAssignC2SPacket(slotIdx, selectedSpellId));
                    selectedSpellId = null;
                    rebuildSlotButtons();
                }
            }).bounds(slotX, slotY, btnSize, btnSize).build());
        }

        // Spell list buttons (scrollable)
        int listY = searchY + 18;
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(spellEntries.size(), startIdx + MAX_VISIBLE);

        for (int i = startIdx; i < endIdx; i++) {
            SpellEntry entry = spellEntries.get(i);
            int entryY = listY + (i - startIdx) * 18;

            if (entry.spell != null) {
                final Spell spell = entry.spell;
                addRenderableWidget(Button.builder(
                        Component.literal(spell.getDisplayName()),
                        btn -> selectedSpellId = spell.getId()
                ).bounds(listX, entryY, LEFT_W - 8, 16).build());
            }
        }

        // Scroll buttons (up/down arrows on the left panel)
        int maxScroll = Math.max(0, spellEntries.size() - MAX_VISIBLE);
        if (maxScroll > 0) {
            int arrowCenterX = panelX + LEFT_W / 2;

            // Up arrow
            if (scrollOffset > 0) {
                addRenderableWidget(Button.builder(Component.literal("\u25B2"), btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    rebuildSlotButtons();
                }).bounds(arrowCenterX - 6, panelY + 14, 12, 10).build());
            }

            // Down arrow
            if (scrollOffset < maxScroll) {
                addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                    scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                    rebuildSlotButtons();
                }).bounds(arrowCenterX - 6, panelY + PANEL_H - 20, 12, 10).build());
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, spellEntries.size() - MAX_VISIBLE);
        scrollOffset = MathUtils.clampInt((int) (scrollOffset - scrollY), 0, maxScroll);
        rebuildSlotButtons();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        SpellMenuRenderHelper.renderFrame(graphics, font, width, height, PANEL_W, PANEL_H, LEFT_W);
        SpellMenuRenderHelper.renderSpellList(graphics, font, width, height, PANEL_W, PANEL_H, LEFT_W,
                MAX_VISIBLE, spellEntries, selectedSpellId, scrollOffset);
        SpellMenuRenderHelper.renderSelectedSpellPanel(graphics, font, width, height, PANEL_W, PANEL_H,
                LEFT_W, selectedSpellId);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record SpellEntry(@Nullable Spell spell, SpellCategory category) {
    }
}
