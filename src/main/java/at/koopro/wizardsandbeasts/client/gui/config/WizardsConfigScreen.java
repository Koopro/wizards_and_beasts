package at.koopro.wizardsandbeasts.client.gui.config;

import at.koopro.wizardsandbeasts.Config;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
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
            Map.entry("perfProfile", "Performance"),
            Map.entry("beamTargetScanIntervalTicks", "Performance"),
            Map.entry("beamChannelEffectIntervalTicks", "Performance"),
            Map.entry("enableDebugTools", "Debug"),
            Map.entry("debugLogSpellGateReasons", "Debug"),
            Map.entry("debugLogCloakVisibility", "Debug"));
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
        drawHeader(graphics);
        drawStamp(graphics);
        if (categories.isEmpty()) {
            graphics.drawCenteredString(font, "Configuration file is not loaded — no settings to display.",
                    width / 2, height / 2 - 4, ConfigWidgets.INK);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, ConfigWidgets.PARCHMENT);
    }

    private void drawHeader(GuiGraphics graphics) {
        String spaced = letterSpaced(HEADER);
        String drawn = font.width(spaced) <= width - 24 ? spaced : HEADER;
        graphics.drawCenteredString(font, drawn, width / 2, 16, ConfigWidgets.INK);
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
        graphics.drawString(font, line1, (int) ((innerWidth - 8 - font.width(line1) * scale) / 2 / scale), 0, ConfigWidgets.STAMP_RED);
        graphics.drawString(font, line2, 0, 12, ConfigWidgets.STAMP_RED);
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
            int fill = darkArts ? 0xFF2B1414 : (isHovered() ? 0xFFEFE3C4 : 0xFFF0E7CC);
            int border = darkArts ? ConfigWidgets.STAMP_RED : ConfigWidgets.INK;
            int text = darkArts ? 0xFFE0B0B0 : ConfigWidgets.INK;
            int x0 = getX();
            int y0 = getY();
            int x1 = x0 + getWidth();
            int y1 = y0 + getHeight();
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
            // Four-corner rounded border simulation: edge strips inset by 1px at the corners.
            graphics.hLine(x0 + 2, x1 - 3, y0, border);
            graphics.hLine(x0 + 2, x1 - 3, y1 - 1, border);
            graphics.vLine(x0, y0 + 2, y1 - 3, border);
            graphics.vLine(x1 - 1, y0 + 2, y1 - 3, border);
            graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, border);
            graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, border);
            graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, border);
            graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, border);

            String glyph = category.glyph();
            if (font.width(glyph) <= 0) {
                glyph = category.abbrev();
            }
            graphics.drawCenteredString(font, glyph, x0 + getWidth() / 2, y0 + 16, text);
            graphics.drawCenteredString(font, category.name(), x0 + getWidth() / 2, y1 - 22, text);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
