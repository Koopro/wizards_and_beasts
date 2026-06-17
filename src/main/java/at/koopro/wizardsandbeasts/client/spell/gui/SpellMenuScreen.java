package at.koopro.wizardsandbeasts.client.spell.gui;

import at.koopro.wizardsandbeasts.client.ModTextures;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.client.skill.gui.SkillScreenRouter;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.util.MathUtils;
import net.minecraft.client.Minecraft;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.gui.WizardsAndBeastsUiTokens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SpellMenuScreen extends Screen {

    private static final Identifier SLOT_TEX = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/spell_menu/slot.png");
    private static final Identifier SLOT_ACTIVE_TEX = Identifier.fromNamespaceAndPath(
            "wizards_and_beasts", "textures/gui/spell_menu/slot_active.png");

    private static final int PANEL_W = WizardsAndBeastsUiTokens.SpellMenu.PANEL_WIDTH;
    private static final int PANEL_H = WizardsAndBeastsUiTokens.SpellMenu.PANEL_HEIGHT;
    private static final int LEFT_W = WizardsAndBeastsUiTokens.SpellMenu.LEFT_WIDTH;

    @Nullable
    private String selectedSpellId = null;
    private final List<SpellEntry> spellEntries = new ArrayList<>();
    private int scrollOffset = 0;
    private String searchQuery = "";
    private ScreenLayoutScaler layout;
    private final int[][] slotPositions = new int[4][2];
    private int slotBtnSize;

    public SpellMenuScreen() {
        super(Component.literal("Spell Menu"));
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, PANEL_W, PANEL_H);
        rebuildSpellList();
        rebuildSlotButtons();
    }

    private void rebuildSpellList() {
        spellEntries.clear();
        PlayerSpellData data = ClientSpellDataState.get();
        Heritage type = ClientHeritageDataState.get().getSelectedHeritage();

        String q = searchQuery == null ? "" : searchQuery.toLowerCase();

        for (SpellCategory category : SpellCategory.values()) {
            boolean hasSpells = false;
            for (Spell spell : Spells.all()) {
                if (spell.getCategory() == category
                        && data.knowsSpell(spell.getId())
                        && !ObscurialRules.isObscurialAbility(spell)
                        && ObscurialRules.canHeritageUseSpell(type, spell)) {
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

        int maxVisible = getMaxVisible();
        int maxScroll = Math.max(0, spellEntries.size() - maxVisible);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void rebuildSlotButtons() {
        clearWidgets();

        int panelX = (width - PANEL_W) / 2;
        int panelY = (height - PANEL_H) / 2;
        int panelW = layout.panelW();
        int panelH = layout.panelH();
        panelX = layout.panelX();
        panelY = layout.panelY();
        int leftW = layout.s(LEFT_W);

        addRenderableWidget(Button.builder(Component.literal("Skills"),
                btn -> SkillScreenRouter.openForCurrentPlayer())
                .bounds(panelX + panelW - layout.s(62), panelY + layout.s(4), layout.s(56), layout.s(14))
                .build());

        // Search box above the spell list
        int listX = panelX + layout.s(WizardsAndBeastsUiTokens.SpellMenu.LEFT_PADDING);
        int searchY = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING);
        EditBox searchBox = new EditBox(this.font, listX, searchY,
                leftW - layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_SIDE_PADDING),
                layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT),
                Component.literal("Search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(text -> {
            searchQuery = text.toLowerCase();
            scrollOffset = 0;
            rebuildSpellList();
            rebuildSlotButtons();
        });
        addRenderableWidget(searchBox);

        int cx = panelX + leftW + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_X_OFFSET);
        int cy = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_CENTER_Y_OFFSET);
        int btnSize = layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_BUTTON_SIZE);
        int spacing = layout.s(WizardsAndBeastsUiTokens.SpellMenu.SLOT_SPACING);

        int[][] offsets = {
                {0, -spacing},
                {spacing, 0},
                {0, spacing},
                {-spacing, 0}
        };
        String[] labels = {"Up", "Right", "Down", "Left"};

        for (int i = 0; i < 4; i++) {
            int slotX = cx + offsets[i][0] - btnSize / 2;
            int slotY = cy + offsets[i][1] - btnSize / 2;
            final int slotIdx = i;
            slotPositions[i][0] = slotX;
            slotPositions[i][1] = slotY;
            slotBtnSize = btnSize;

            addRenderableWidget(Button.builder(Component.literal(""), btn -> {
                if (selectedSpellId != null) {
                    ClientSpellDataState.get().setLoadoutSpell(slotIdx, selectedSpellId);
                    ClientPacketDistributor.sendToServer(new SpellAssignC2SPayload(slotIdx, selectedSpellId));
                    selectedSpellId = null;
                    rebuildSlotButtons();
                }
            }).bounds(slotX, slotY, btnSize, btnSize).build());
        }

        // Spell list buttons (scrollable)
        int listY = searchY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT + WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP);
        int maxVisible = getMaxVisible();
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(spellEntries.size(), startIdx + maxVisible);

        for (int i = startIdx; i < endIdx; i++) {
            SpellEntry entry = spellEntries.get(i);
            int entryY = listY + (i - startIdx) * layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING);

            if (entry.spell != null) {
                final Spell spell = entry.spell;
                addRenderableWidget(Button.builder(
                        Component.literal(spell.getDisplayName()),
                        btn -> selectedSpellId = spell.getId()
                ).bounds(listX, entryY,
                        leftW - layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_SIDE_PADDING),
                        layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_HEIGHT)).build());
            }
        }

        // Scroll buttons (up/down arrows on the left panel)
        int maxScroll = Math.max(0, spellEntries.size() - maxVisible);
        if (maxScroll > 0) {
            int arrowCenterX = panelX + leftW / 2;

            // Up arrow
            if (scrollOffset > 0) {
                addRenderableWidget(Button.builder(Component.literal("\u25B2"), btn -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    rebuildSlotButtons();
                }).bounds(arrowCenterX - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_HALF_WIDTH),
                        panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_UP_BUTTON_Y),
                        layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_WIDTH),
                        layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_HEIGHT)).build());
            }

            // Down arrow
            if (scrollOffset < maxScroll) {
                addRenderableWidget(Button.builder(Component.literal("\u25BC"), btn -> {
                    scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                    rebuildSlotButtons();
                }).bounds(arrowCenterX - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_HALF_WIDTH),
                        panelY + panelH - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_DOWN_BUTTON_BOTTOM_OFFSET),
                        layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_WIDTH),
                        layout.s(WizardsAndBeastsUiTokens.SpellMenu.SCROLL_ARROW_HEIGHT)).build());
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, spellEntries.size() - getMaxVisible());
        scrollOffset = MathUtils.clampInt((int) (scrollOffset - scrollY), 0, maxScroll);
        rebuildSlotButtons();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        SpellMenuRenderHelper.renderFrame(graphics, font, width, height, PANEL_W, PANEL_H, LEFT_W, layout);
        SpellMenuRenderHelper.renderSpellList(graphics, font, width, height, PANEL_W, PANEL_H, LEFT_W,
                getMaxVisible(), spellEntries, selectedSpellId, scrollOffset, layout);
        SpellMenuRenderHelper.renderSelectedSpellPanel(graphics, font, width, height, PANEL_W, PANEL_H,
                LEFT_W, selectedSpellId, layout);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderSlotIcons(graphics);
        renderSpellListIcons(graphics);
    }

    private void renderSlotIcons(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSpellData data = ClientSpellDataState.get();
        String[] labels = {"Up", "Right", "Down", "Left"};
        int iconSize = Math.max(8, slotBtnSize - 12);
        for (int i = 0; i < 4; i++) {
            int sx = slotPositions[i][0];
            int sy = slotPositions[i][1];
            String spellId = data.getLoadoutSpell(i);
            String name = labels[i];
            // Rune-socket frame drawn over the (invisible) button background.
            graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_TEX, sx, sy, 0f, 0f, slotBtnSize, slotBtnSize, 40, 40, 40, 40);
            if (spellId != null) {
                Spell spell = Spells.byId(spellId);
                if (spell != null) name = spell.getDisplayName();
                Identifier icon = ModTextures.resolveWandHudSpellIcon(mc.getResourceManager(), spellId);
                int ix = sx + (slotBtnSize - iconSize) / 2;
                int iy = sy + (slotBtnSize - iconSize) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0f, 0f, iconSize, iconSize, 92, 92, 92, 92);
                graphics.blit(RenderPipelines.GUI_TEXTURED, SLOT_ACTIVE_TEX, sx, sy, 0f, 0f, slotBtnSize, slotBtnSize, 40, 40, 40, 40);
            }
            int nameX = sx + slotBtnSize / 2 - font.width(name) / 2;
            graphics.drawString(font, name, nameX, sy - font.lineHeight - 1, 0xFFFFFFFF, true);
        }
    }

    private void renderSpellListIcons(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int listX = panelX + layout.s(WizardsAndBeastsUiTokens.SpellMenu.LEFT_PADDING);
        int searchY = panelY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING);
        int listY = searchY + layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT + WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP);
        int maxVisible = getMaxVisible();
        int startIdx = Math.max(0, scrollOffset);
        int endIdx = Math.min(spellEntries.size(), startIdx + maxVisible);
        int iconSize = 12;
        for (int i = startIdx; i < endIdx; i++) {
            var entry = spellEntries.get(i);
            if (entry.spell() == null) continue;
            int entryY = listY + (i - startIdx) * layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING);
            int iconY = entryY + (layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_BUTTON_HEIGHT) - iconSize) / 2;
            Identifier icon = ModTextures.resolveWandHudSpellIcon(mc.getResourceManager(), entry.spell().getId());
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, listX + 1, iconY, 0f, 0f, iconSize, iconSize, 92, 92, 92, 92);
        }
    }

    private int getMaxVisible() {
        int available = layout.panelH()
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.TOP_PADDING)
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_HEIGHT)
                - layout.s(WizardsAndBeastsUiTokens.SpellMenu.SEARCH_TO_LIST_GAP)
                - layout.s(24);
        return Math.max(4, available / Math.max(1, layout.s(WizardsAndBeastsUiTokens.SpellMenu.LIST_ROW_SPACING)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public record SpellEntry(@Nullable Spell spell, SpellCategory category) {
    }
}
