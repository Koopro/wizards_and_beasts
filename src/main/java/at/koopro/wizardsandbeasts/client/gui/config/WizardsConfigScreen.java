package at.koopro.wizardsandbeasts.client.gui.config;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * Main mod configuration screen — Ministry memo board aesthetic: parchment fill,
 * letter-spaced header, CLASSIFIED stamp, and a card grid of config categories
 * revealed with a staggered ink animation.
 */
public class WizardsConfigScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HEADER = "MINISTRY OF MAGIC — AUTHORISED CONFIGURATION FORM";
    private static final Identifier PARCHMENT_TEX = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/config/parchment.png");
    static final Identifier CARD_TEX = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/config/card.png");
    static final Identifier CARD_DARK_TEX = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/config/card_dark.png");
    private static final int PARCHMENT_TILE = 64;
    private static final int CARD_WIDTH = 84;
    private static final int CARD_HEIGHT = 64;
    private static final int CARD_GAP = 12;
    private static final int CARDS_PER_ROW = 4;
    private static final int CARD_STAGGER_MS = 80;

    /** One config key with its live value holder and spec metadata. */
    public record ConfigEntry(String key, ModConfigSpec.ConfigValue<?> value, ModConfigSpec.ValueSpec valueSpec) {}

    /** A themed category of config entries. */
    public record Category(String name, String glyph, String abbrev, List<ConfigEntry> entries) {}

    // Logical subsystem grouping: the spec has no push/pop sections, so keys map here.
    // Unknown future keys fall back to Gameplay.
    private static final Map<String, String> CATEGORY_BY_KEY = Map.ofEntries(
            Map.entry("enforceSpellRequirements", "Gameplay"),
            Map.entry("spellTeacherRequirePayment", "Gameplay"),
            Map.entry("spellTeacherLearnCostKnuts", "Gameplay"),
            Map.entry("enableWandAllegiance", "Gameplay"),
            Map.entry("enableCloakSelfViewRestrictions", "Gameplay"),
            Map.entry("cloakSelfViewRestrictionsDeathlyOnly", "Gameplay"),
            Map.entry("showSpellHudOverlay", "Gameplay"),
            Map.entry("reduceScreenEffects", "Gameplay"),
            Map.entry("perfProfile", "Performance"),
            Map.entry("beamTargetScanIntervalTicks", "Performance"),
            Map.entry("beamChannelEffectIntervalTicks", "Performance"),
            Map.entry("enableDebugTools", "Debug"),
            Map.entry("debugLogSpellGateReasons", "Debug"),
            Map.entry("debugLogCloakVisibility", "Debug"));
    /**
     * Keys this screen must never show, even under the "Gameplay" fallback bucket. Both are administrative
     * rather than gameplay concerns: {@code adminUuids} is a security allow-list, and the module state fed
     * by {@link at.koopro.wizardsandbeasts.module.ModuleConfig} only seeds a brand-new world — turning a
     * module's dial here would silently do nothing on every world that already exists. Both have a real
     * home ({@code /wandb module}), just not this one.
     */
    private static final java.util.Set<String> EXCLUDED_KEYS = java.util.Set.of("adminUuids");
    private static final String EXCLUDED_KEY_PREFIX = "moduleDefaults";

    private static final List<String> CATEGORY_ORDER = List.of("Gameplay", "Performance", "Debug", "Dark Arts");
    private static final Map<String, String> GLYPH_BY_CATEGORY = Map.of(
            "Gameplay", "⚗",
            "Performance", "⚙",
            "Debug", "✒",
            "Dark Arts", "☠");
    private static final Map<String, String> ABBREV_BY_CATEGORY = Map.of(
            "Gameplay", "GP",
            "Performance", "PF",
            "Debug", "DB",
            "Dark Arts", "DA");

    private final Screen parent;
    private final List<Category> categories;
    private final InkRevealRenderer inkReveal = new InkRevealRenderer();

    public WizardsConfigScreen(Screen parent) {
        super(Component.literal("Wizards & Beasts Configuration"));
        this.parent = parent;
        this.categories = buildCategories();
    }

    private static List<Category> buildCategories() {
        List<Category> result = new ArrayList<>();
        if (!Config.SPEC.isLoaded()) {
            LOGGER.warn("Wizards & Beasts config is not loaded; config screen will show no settings");
            return result;
        }
        Map<String, List<ConfigEntry>> buckets = new LinkedHashMap<>();
        CATEGORY_ORDER.forEach(name -> buckets.put(name, new ArrayList<>()));
        for (Map.Entry<String, Object> entry : Config.SPEC.getValues().valueMap().entrySet()) {
            if (EXCLUDED_KEYS.contains(entry.getKey()) || entry.getKey().startsWith(EXCLUDED_KEY_PREFIX)) {
                continue; // administrative, not gameplay — see EXCLUDED_KEYS javadoc
            }
            if (!(entry.getValue() instanceof ModConfigSpec.ConfigValue<?> configValue)) {
                continue;
            }
            try {
                configValue.get();
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping config key '{}' — value holder not available: {}", entry.getKey(), e.toString());
                continue;
            }
            String categoryName = CATEGORY_BY_KEY.getOrDefault(entry.getKey(), "Gameplay");
            buckets.get(categoryName).add(new ConfigEntry(entry.getKey(), configValue, configValue.getSpec()));
        }
        for (Map.Entry<String, List<ConfigEntry>> bucket : buckets.entrySet()) {
            if (!bucket.getValue().isEmpty()) {
                result.add(new Category(bucket.getKey(),
                        GLYPH_BY_CATEGORY.getOrDefault(bucket.getKey(), "?"),
                        ABBREV_BY_CATEGORY.getOrDefault(bucket.getKey(), "??"),
                        List.copyOf(bucket.getValue())));
            }
        }
        return result;
    }

    @Override
    protected void init() {
        super.init();
        inkReveal.reset();

        int count = categories.size();
        if (count > 0) {
            int perRow = Math.min(count, CARDS_PER_ROW);
            int rows = (count + perRow - 1) / perRow;
            int gridHeight = rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
            int startY = Math.max(58, height / 2 - gridHeight / 2);
            for (int i = 0; i < count; i++) {
                int row = i / perRow;
                int inRow = Math.min(perRow, count - row * perRow);
                int rowWidth = inRow * CARD_WIDTH + (inRow - 1) * CARD_GAP;
                int x = width / 2 - rowWidth / 2 + (i % perRow) * (CARD_WIDTH + CARD_GAP);
                int y = startY + row * (CARD_HEIGHT + CARD_GAP);
                CategoryCard card = new CategoryCard(x, y, categories.get(i), i + 1);
                addRenderableWidget(card);
                inkReveal.register(card, i, CARD_STAGGER_MS);
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20)
                .build());
    }

    private void openCategory(Category category, int ordinal) {
        if ("Dark Arts".equals(category.name())) {
            Minecraft.getInstance().setScreen(new DarkArtsGateScreen(this,
                    () -> new WizardsConfigCategoryScreen(this, category, ordinal)));
        } else {
            Minecraft.getInstance().setScreen(new WizardsConfigCategoryScreen(this, category, ordinal));
        }
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        inkReveal.update();
        super.render(graphics, mouseX, mouseY, partialTick); // triggers renderBackground → widgets → Done button
        drawHeader(graphics);   // drawn after background fill, on top of widgets
        drawStamp(graphics);
        if (categories.isEmpty()) {
            drawCenteredNoShadow(graphics, "Configuration file is not loaded — no settings to display.",
                    width / 2, height / 2 - 4, ConfigWidgets.INK);
        }
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ConfigWidgets.PARCHMENT);
        McStylePanel.drawTiled(graphics, PARCHMENT_TEX, 0, 0, width, height, PARCHMENT_TILE);
    }

    /**
     * Centered text without a drop shadow. Vanilla {@code drawCenteredString} always draws a
     * 1px-offset shadow; against this screen's light parchment the dark ink shadow reads as a
     * doubled/ghosted glyph, so all ink text on this screen is drawn shadow-free.
     */
    private void drawCenteredNoShadow(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void drawHeader(GuiGraphics graphics) {
        String spaced = letterSpaced(HEADER);
        String drawn = font.width(spaced) <= width - 24 ? spaced : HEADER;
        drawCenteredNoShadow(graphics, drawn, width / 2, 16, ConfigWidgets.INK);
        int ruleHalf = Math.min(font.width(drawn) / 2 + 8, width / 2 - 12);
        graphics.hLine(width / 2 - ruleHalf, width / 2 + ruleHalf, 28, ConfigWidgets.INK);
        graphics.hLine(width / 2 - ruleHalf, width / 2 + ruleHalf, 30, ConfigWidgets.INK);
    }

    private void drawStamp(GuiGraphics graphics) {
        String line1 = "CLASSIFIED";
        String line2 = "MOD CONFIGURATION";
        float scale = 0.75F;
        int innerWidth = (int) (font.width(line2) * scale) + 8;
        int innerHeight = 24;
        int x1 = width - 8;
        int x0 = x1 - innerWidth;
        int y0 = 36;
        int y1 = y0 + innerHeight;
        graphics.fill(x0 - 2, y0 - 2, x1 + 2, y0, ConfigWidgets.STAMP_RED);
        graphics.fill(x0 - 2, y1, x1 + 2, y1 + 2, ConfigWidgets.STAMP_RED);
        graphics.fill(x0 - 2, y0, x0, y1, ConfigWidgets.STAMP_RED);
        graphics.fill(x1, y0, x1 + 2, y1, ConfigWidgets.STAMP_RED);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x0 + 4, y0 + 4);
        pose.scale(scale, scale);
        graphics.drawString(font, line1, (int) ((innerWidth - 8 - font.width(line1) * scale) / 2 / scale), 0, ConfigWidgets.STAMP_RED, false);
        graphics.drawString(font, line2, 0, 12, ConfigWidgets.STAMP_RED, false);
        pose.popMatrix();
    }

    private static String letterSpaced(String text) {
        StringBuilder out = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            out.append(text.charAt(i));
            if (i < text.length() - 1) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Memo-board category card: bordered parchment tile with glyph and name. */
    private final class CategoryCard extends AbstractButton {
        private final Category category;
        private final int ordinal;

        CategoryCard(int x, int y, Category category, int ordinal) {
            super(x, y, CARD_WIDTH, CARD_HEIGHT, Component.literal(category.name()));
            this.category = category;
            this.ordinal = ordinal;
        }

        @Override
        public void onPress(@NonNull InputWithModifiers input) {
            openCategory(category, ordinal);
        }

        @Override
        protected void renderContents(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean darkArts = "Dark Arts".equals(category.name());
            int text = darkArts ? 0xFFE0B0B0 : ConfigWidgets.INK;
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            int y1 = y0 + getHeight();
            // Textured memo card (parchment / dark-arts variant); border + paper drawn in the art.
            McStylePanel.drawTexture(graphics, darkArts ? CARD_DARK_TEX : CARD_TEX,
                    x0, y0, getWidth(), getHeight(), CARD_WIDTH, CARD_HEIGHT);
            if (isHovered()) {
                graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, darkArts ? 0x18FF8080 : 0x22FFFFFF);
            }

            String glyph = category.glyph();
            if (font.width(glyph) <= 0) {
                glyph = category.abbrev();
            }
            drawCenteredNoShadow(graphics, glyph, x0 + getWidth() / 2, y0 + 16, text);
            drawCenteredNoShadow(graphics, category.name(), x0 + getWidth() / 2, y1 - 22, text);
        }

        @Override
        protected void renderDefaultLabel(@NonNull ActiveTextCollector collector) {
            // Label (category name) is drawn in renderContents() — suppress AbstractButton's
            // default message label so it is not drawn a second time over the contents.
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
