package at.koopro.wizardsandbeasts.client.bestiary.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;

import at.koopro.wizardsandbeasts.bestiary.BestiaryCategory;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.bestiary.CreatureProfile;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.PlayerBestiaryData;
import at.koopro.wizardsandbeasts.bestiary.journal.IllustrationStage;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalBook;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalFacts;
import at.koopro.wizardsandbeasts.bestiary.journal.JournalLayout;
import at.koopro.wizardsandbeasts.bestiary.journal.ResearchProgress;
import at.koopro.wizardsandbeasts.bestiary.journal.ResearchRewards;
import at.koopro.wizardsandbeasts.client.bestiary.ClientBestiaryCache;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Bestiary as a Magical Naturalist's Field Journal: an index, and for each creature a two-page spread — the
 * illustrated plate on the left, the notes on the right.
 *
 * <p>Nothing here is per-creature. What a page says, and from which depth, is {@link JournalLayout} (datapack) read
 * through {@code JournalReveal}; the words are {@link JournalPageWriter}'s; the facts that live in other datapacks
 * (temperament, ecology, feeding, harvest) arrive as {@link JournalFacts} from the server; the plate is whichever
 * stage of art the player has earned ({@link IllustrationStage}), falling back stage by stage to what exists, and to
 * the live creature for a studied page without a painting. Depth is always the server's word
 * ({@code EncounterRule}); the client only draws it.
 *
 * <p>Drawn in the {@link GuiSkin#FIELD_NOTEBOOK} material, with that skin's own ink (GUI_DESIGN_SYSTEM.md, Materials).
 */
public final class BestiaryScreen extends Screen {

    private static final GuiSkin SKIN = GuiSkin.FIELD_NOTEBOOK;
    private static final int W = 400;
    private static final int H = 256;
    private static final int PAD = 12;
    private static final int SPINE = W / 2;
    private static final int PAGE_W = SPINE - PAD - 8;
    private static final int LEFT = PAD;
    private static final int RIGHT = SPINE + 8;
    private static final int TOP = 14;
    private static final int BOTTOM = H - 16;
    private static final int LINE = 10;
    private static final int GAP = 4;
    private static final int ROW = 12;

    private static final int PLATE = 96;
    private static final int PLATE_X = LEFT + (PAGE_W - PLATE) / 2;
    private static final int PLATE_Y = TOP + 16;
    private static final int PLATE_TEXT_Y = PLATE_Y + PLATE + 16;
    private static final int NOTES_Y = TOP + 16;
    private static final int LIST_Y = TOP + 36;

    private static final int INK = SKIN.ink();
    private static final int QUIET = UiContrast.readableOn(SKIN.muted(), SKIN.base());
    private static final int HEADING = UiContrast.readableOn(WizardsPalette.PAGE_RUBRIC, SKIN.base());
    private static final int DONE = UiContrast.readableOn(WizardsPalette.PAGE_GOOD, SKIN.base());

    private int x;
    private int y;
    private float guiScale = 1.0f;
    private @Nullable ThemedTextField search;

    private final List<BestiaryEntry> entries = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<BestiaryCategory, Boolean> collapsed = new EnumMap<>(BestiaryCategory.class);
    private @Nullable Identifier open;
    private int listScroll;
    private int plateScroll;
    private int notesScroll;
    private int plateScrollMax;
    private int notesScrollMax;
    private int listScrollMax;
    private boolean showSignature;
    private boolean showLive;

    private final Map<Identifier, LivingEntity> renderEntities = new HashMap<>();
    private final Set<Identifier> renderEntityMisses = new HashSet<>();
    private final Map<Identifier, Boolean> artExists = new HashMap<>();

    public BestiaryScreen() {
        super(Component.translatable("gui.wizards_and_beasts.bestiary.title"));
    }

    @Override
    protected void init() {
        guiScale = GuiScaleHelper.computeScale(W, H, width, height, GuiScaleHelper.DEFAULT_MARGIN);
        x = GuiScaleHelper.clampedLeft(Math.round(W * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        y = GuiScaleHelper.clampedTop(Math.round(H * guiScale), height, GuiScaleHelper.DEFAULT_MARGIN);
        search = new ThemedTextField(font, x + Math.round(LEFT * guiScale), y + Math.round((TOP + 16) * guiScale),
                Math.round(PAGE_W * guiScale), Math.round(14 * guiScale),
                Component.translatable("gui.wizards_and_beasts.bestiary.search")).skin(SKIN);
        search.setResponder(value -> rebuildRows());
        search.visible = open == null;
        addRenderableWidget(search);
        entries.clear();
        entries.addAll(BestiaryEntryRegistry.clientGetAll());
        entries.sort(Comparator.comparing((BestiaryEntry e) -> e.category().name()).thenComparingInt(BestiaryEntry::sortOrder));
        rebuildRows();
    }

    private static PlayerBestiaryData data() {
        return ClientBestiaryCache.get();
    }

    private static DiscoveryTier tierOf(Identifier id) {
        return data().tiers().getOrDefault(id, DiscoveryTier.UNKNOWN);
    }

    // ── index ───────────────────────────────────────────────────────────────────────────────────

    private void rebuildRows() {
        rows.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase();
        for (BestiaryCategory category : BestiaryCategory.values()) {
            List<BestiaryEntry> shown = new ArrayList<>();
            for (BestiaryEntry e : entries) {
                // An undiscovered creature cannot be searched for by name: the name is not the player's yet.
                boolean known = tierOf(e.id()) != DiscoveryTier.UNKNOWN;
                if (e.category() == category && (q.isBlank()
                        || known && e.displayName().getString().toLowerCase().contains(q))) {
                    shown.add(e);
                }
            }
            if (shown.isEmpty()) {
                continue;
            }
            rows.add(new Row(category, null));
            if (!collapsed.getOrDefault(category, false)) {
                shown.forEach(e -> rows.add(new Row(null, e)));
            }
        }
        listScrollMax = Math.max(0, rows.size() - (BOTTOM - LIST_Y) / ROW);
        listScroll = Math.min(listScroll, listScrollMax);
    }

    private void renderIndex(GuiGraphics gg) {
        gg.drawString(font, title, x + LEFT, y + TOP, INK, false);
        int rowY = y + LIST_Y;
        for (int i = listScroll; i < rows.size() && rowY + ROW <= y + BOTTOM; i++) {
            Row row = rows.get(i);
            if (row.category != null) {
                String arrow = collapsed.getOrDefault(row.category, false) ? "▶ " : "▼ ";
                gg.drawString(font, Component.literal(arrow).append(Component.translatable(
                        "bestiary.wizards_and_beasts.category." + row.category.name().toLowerCase())), x + LEFT, rowY + 2, HEADING, false);
            } else if (row.entry != null) {
                DiscoveryTier tier = tierOf(row.entry.id());
                Component name = tier == DiscoveryTier.UNKNOWN
                        ? Component.translatable("bestiary.wizards_and_beasts.entry.unknown") : row.entry.displayName();
                gg.drawString(font, Component.literal(font.plainSubstrByWidth(name.getString(), PAGE_W - 40)),
                        x + LEFT + 6, rowY + 2, tier == DiscoveryTier.UNKNOWN ? QUIET : INK, false);
                drawPips(gg, x + LEFT + PAGE_W - 30, rowY + 4, tier,
                        data().historyOf(row.entry.id()).complete());
            }
            rowY += ROW;
        }
        if (rows.isEmpty()) {
            gg.drawWordWrap(font, Component.translatable(entries.isEmpty()
                            ? "bestiary.wizards_and_beasts.empty.no_entries" : "bestiary.wizards_and_beasts.empty.no_matches"),
                    x + LEFT, y + LIST_Y, PAGE_W, QUIET, false);
        }
        drawBar(gg, x + LEFT + PAGE_W + 2, y + LIST_Y, y + BOTTOM, listScroll, listScrollMax);

        // The facing page: the journal's own front matter — how far the book has got.
        int[] counts = new int[DiscoveryTier.values().length];
        int complete = 0;
        for (BestiaryEntry e : entries) {
            counts[tierOf(e.id()).ordinal()]++;
            complete += data().historyOf(e.id()).complete() ? 1 : 0;
        }
        // A table, not a column of sentences: "Encountered: 0 / Observed: 0 / ..." read as a log dump.
        // Tier names on the left, counts aligned on the right, how much of the book is written as a bar,
        // then the one line on how pages fill, as a quiet footnote.
        UiSurface page = UiSurface.of(SKIN);
        int fx = x + RIGHT;
        int fw = PAGE_W;
        int fy = Ui.sectionHeader(gg, font, page, fx, y + TOP, fw,
                Component.translatable("bestiary.wizards_and_beasts.journal.front.title"), null);
        for (DiscoveryTier tier : DiscoveryTier.values()) {
            if (tier != DiscoveryTier.UNKNOWN) {
                fy = Ui.statRow(gg, font, page, fx, fy, fw, tier.displayName(),
                        Component.literal(String.valueOf(counts[tier.ordinal()])));
            }
        }
        fy += WizardsMetrics.SPACE_S;
        int written = entries.size() - counts[0];
        fy = Ui.labelledBar(gg, font, page, fx, fy, fw,
                Component.translatable("bestiary.wizards_and_beasts.journal.front.written"),
                Component.literal(written + " / " + entries.size()),
                entries.isEmpty() ? 0.0f : written / (float) entries.size(), page.ink2());
        fy = Ui.statRow(gg, font, page, fx, fy, fw,
                Component.translatable("bestiary.wizards_and_beasts.journal.front.complete_label"),
                Component.literal(String.valueOf(complete)), complete > 0 ? page.good() : page.ink());
        fy += WizardsMetrics.SPACE_M;
        UiText.drawWrapped(gg, font, page, UiText.Role.CAPTION,
                Component.translatable("bestiary.wizards_and_beasts.journal.front.how"), fx, fy, fw, 4);
    }

    private void drawPips(GuiGraphics gg, int px, int py, DiscoveryTier tier, boolean complete) {
        for (int i = 1; i <= 4; i++) {
            int c = i <= tier.tierIndex() ? (complete ? DONE : INK) : SKIN.muted() & 0x55FFFFFF;
            gg.fill(px + (i - 1) * 6, py, px + (i - 1) * 6 + 4, py + 4, c);
        }
    }

    // ── an entry ────────────────────────────────────────────────────────────────────────────────

    private void renderEntry(GuiGraphics gg, BestiaryEntry entry) {
        DiscoveryTier tier = tierOf(entry.id());
        JournalLayout layout = JournalBook.clientLayout();
        JournalFacts facts = JournalBook.clientFacts(entry.id());
        gg.drawString(font, Component.translatable("bestiary.wizards_and_beasts.journal.back"), x + LEFT, y + TOP, QUIET, false);
        Component name = tier == DiscoveryTier.UNKNOWN
                ? Component.translatable("bestiary.wizards_and_beasts.entry.unobserved") : entry.displayName();
        gg.drawString(font, name, x + LEFT + (PAGE_W - font.width(name)) / 2, y + TOP, INK, false);

        // The plate: the stage this depth has earned, or the signature illustration when asked for.
        IllustrationStage stage = IllustrationStage.forTier(tier);
        McStylePanel.drawSkinInset(gg, SKIN, x + PLATE_X - 2, y + PLATE_Y - 2, PLATE + 4, PLATE + 4);
        if (stage == null) {
            gg.drawWordWrap(font, DiscoveryTier.ENCOUNTERED.unlockHint(), x + PLATE_X + 4, y + PLATE_Y + 30, PLATE - 8, QUIET, false);
        } else if (liveWanted(entry, stage)) {
            // drawn after the scaled pass, in screen space (renderLive)
        } else if (!drawPlate(gg, entry, showSignature ? IllustrationStage.SIGNATURE : stage)) {
            drawFallbackPlate(gg, entry, stage);
        }
        if (stage != null) {
            Component caption = Component.translatable("bestiary.wizards_and_beasts.journal.stage."
                    + (showSignature ? "signature" : stage.getSerializedName()));
            gg.drawString(font, caption, x + LEFT, y + PLATE_Y + PLATE + 4, QUIET, false);
            drawToggles(gg, entry, tier);
        }

        PlayerBestiaryData data = data();
        List<JournalPageWriter.Block> plate = JournalPageWriter.write(JournalLayout.Page.PLATE, entry, tier, data, facts, layout);
        plateScrollMax = drawBlocks(gg, plate, x + LEFT, y + PLATE_TEXT_Y, y + BOTTOM, plateScroll);
        List<JournalPageWriter.Block> notes = JournalPageWriter.write(JournalLayout.Page.NOTES, entry, tier, data, facts, layout);
        if (tier == DiscoveryTier.UNKNOWN) {
            notes = List.of(new JournalPageWriter.Block(Component.translatable("bestiary.wizards_and_beasts.journal.unrecorded"),
                    JournalPageWriter.Ink.QUIET));
        }
        notesScrollMax = drawBlocks(gg, notes, x + RIGHT, y + NOTES_Y, y + BOTTOM, notesScroll);
        gg.drawString(font, Component.literal("‹"), x + RIGHT, y + H - 13, QUIET, false);
        gg.drawString(font, Component.literal("›"), x + W - PAD - 6, y + H - 13, QUIET, false);
    }

    /** The live-model and signature switches under the plate, when the page has earned them. */
    private void drawToggles(GuiGraphics gg, BestiaryEntry entry, DiscoveryTier tier) {
        int ty = y + PLATE_Y + PLATE + 4;
        if (tier.atLeast(DiscoveryTier.STUDIED) && entry.entityType().isPresent()) {
            gg.drawString(font, Component.translatable("bestiary.wizards_and_beasts.journal.live"),
                    x + LEFT + PAGE_W - 40, ty, showLive ? INK : QUIET, false);
        }
        if (signatureAvailable(entry, tier)) {
            gg.drawString(font, Component.translatable("bestiary.wizards_and_beasts.journal.signature_toggle"),
                    x + LEFT + PAGE_W - 82, ty, showSignature ? INK : QUIET, false);
        }
    }

    private boolean signatureAvailable(BestiaryEntry entry, DiscoveryTier tier) {
        return tier == DiscoveryTier.KNOWN && entry.profile().map(CreatureProfile::hasSignature).orElse(false)
                && exists(entry.journal().textureFor(IllustrationStage.SIGNATURE, entry.id()));
    }

    /** Draws the nearest existing art at or below {@code stage}; false when there is none. */
    private boolean drawPlate(GuiGraphics gg, BestiaryEntry entry, IllustrationStage stage) {
        if (showLive && stage != IllustrationStage.SIGNATURE) {
            return false;
        }
        for (IllustrationStage s = stage; s != null; s = s.previous()) {
            Identifier texture = entry.journal().textureFor(s, entry.id());
            if (exists(texture)) {
                gg.blit(RenderPipelines.GUI_TEXTURED, texture, x + PLATE_X, y + PLATE_Y, 0, 0, PLATE, PLATE,
                        PLATE, PLATE, PLATE, PLATE);
                return true;
            }
            if (s == IllustrationStage.SIGNATURE) {
                return false;
            }
        }
        return false;
    }

    /**
     * No painted plate: the pre-journal icons, at the honesty the stage allows — a silhouette in ink for one met, the
     * same shape in graphite for one watched, the coloured icon from then on.
     */
    private void drawFallbackPlate(GuiGraphics gg, BestiaryEntry entry, IllustrationStage stage) {
        boolean colour = stage.ordinal() >= IllustrationStage.PORTRAIT.ordinal();
        Identifier texture = colour ? entry.iconTexture() : entry.silhouetteTexture();
        int tint = stage == IllustrationStage.SKETCH ? 0xAA555350 : 0xFFFFFFFF;
        gg.blit(RenderPipelines.GUI_TEXTURED, texture, x + PLATE_X, y + PLATE_Y, 0, 0, PLATE, PLATE, 32, 32, 32, 32, tint);
    }

    /** A studied page without a painting shows the live creature, and any studied page can on request. */
    private boolean liveWanted(BestiaryEntry entry, IllustrationStage stage) {
        if (stage.ordinal() < IllustrationStage.PORTRAIT.ordinal() || showSignature || entry.entityType().isEmpty()) {
            return false;
        }
        return showLive || !exists(entry.journal().textureFor(stage, entry.id())) && getRenderEntity(entry) != null;
    }

    private boolean exists(Identifier texture) {
        return artExists.computeIfAbsent(texture, t -> minecraft != null && minecraft.getResourceManager().getResource(t).isPresent());
    }

    /** Draws blocks top-down from {@code skip} lines in; returns how many lines could still be scrolled. */
    private int drawBlocks(GuiGraphics gg, List<JournalPageWriter.Block> blocks, int left, int top, int bottom, int skip) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (JournalPageWriter.Block block : blocks) {
            if (block.ink() == JournalPageWriter.Ink.HEADING && !lines.isEmpty()) {
                lines.add(null);
                colours.add(0);
            }
            for (FormattedCharSequence seq : font.split(block.text(), PAGE_W - 6)) {
                lines.add(seq);
                colours.add(switch (block.ink()) {
                    case HEADING -> HEADING;
                    case BODY -> INK;
                    case QUIET, STRUCK -> QUIET;
                    case DONE -> DONE;
                });
            }
        }
        int visible = (bottom - top) / LINE;
        int max = Math.max(0, lines.size() - visible);
        int ly = top;
        for (int i = Math.min(skip, max); i < lines.size() && ly + LINE <= bottom; i++) {
            if (lines.get(i) == null) {
                ly += GAP;
                continue;
            }
            gg.drawString(font, lines.get(i), left, ly, colours.get(i), false);
            ly += LINE;
        }
        drawBar(gg, left + PAGE_W - 2, top, bottom, skip, max);
        return max;
    }

    private void drawBar(GuiGraphics gg, int bx, int top, int bottom, int at, int max) {
        if (max <= 0) {
            return;
        }
        int track = bottom - top;
        int thumb = Math.max(8, track / (max + 1));
        int ty = top + (track - thumb) * Math.min(at, max) / max;
        gg.fill(bx, top, bx + 2, bottom, SKIN.muted() & 0x55FFFFFF);
        gg.fill(bx, ty, bx + 2, ty + thumb, QUIET);
    }

    // ── live model ──────────────────────────────────────────────────────────────────────────────

    private @Nullable LivingEntity getRenderEntity(BestiaryEntry entry) {
        if (entry.entityType().isEmpty() || minecraft == null || minecraft.level == null) {
            return null;
        }
        Identifier typeId = entry.entityType().get();
        if (renderEntityMisses.contains(typeId)) {
            return null;
        }
        LivingEntity cached = renderEntities.get(typeId);
        if (cached != null) {
            return cached;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(typeId);
        Entity created = type == null ? null : type.create(minecraft.level, EntitySpawnReason.LOAD);
        if (created instanceof LivingEntity living) {
            renderEntities.put(typeId, living);
            return living;
        }
        renderEntityMisses.add(typeId);
        return null;
    }

    private void renderLive(GuiGraphics gg, LivingEntity entity, int mouseX, int mouseY) {
        int sx1 = Math.round(x + PLATE_X * guiScale);
        int sy1 = Math.round(y + PLATE_Y * guiScale);
        int sx2 = Math.round(x + (PLATE_X + PLATE) * guiScale);
        int sy2 = Math.round(y + (PLATE_Y + PLATE) * guiScale);
        int scale = Math.max(4, (int) ((sy2 - sy1) * 0.55f / Math.max(0.5f, entity.getBbHeight())));
        InventoryScreen.renderEntityInInventoryFollowsMouse(gg, sx1, sy1, sx2, sy2, scale, 0.0625f, mouseX, mouseY, entity);
    }

    // ── frame ───────────────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(gg);
        if (search != null) {
            search.visible = open == null;
        }
        var pose = gg.pose();
        pose.pushMatrix();
        pose.translate(x * (1.0f - guiScale), y * (1.0f - guiScale));
        pose.scale(guiScale, guiScale);
        McStylePanel.drawSkinPanel(gg, SKIN, x, y, W, H);
        gg.fill(x + SPINE, y + PAD, x + SPINE + 1, y + H - PAD, SKIN.muted());
        BestiaryEntry entry = open == null ? null : BestiaryEntryRegistry.clientGet(open);
        if (entry == null) {
            renderIndex(gg);
        } else {
            renderEntry(gg, entry);
        }
        pose.popMatrix();
        if (entry != null) {
            IllustrationStage stage = IllustrationStage.forTier(tierOf(entry.id()));
            if (stage != null && liveWanted(entry, stage)) {
                LivingEntity living = getRenderEntity(entry);
                if (living != null) {
                    renderLive(gg, living, mouseX, mouseY);
                }
            }
        }
        super.render(gg, mouseX, mouseY, partialTick);
    }

    // ── input ───────────────────────────────────────────────────────────────────────────────────

    private double dx(double screenX) {
        return (screenX - x) / guiScale;
    }

    private double dy(double screenY) {
        return (screenY - y) / guiScale;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDoubleClick);
        }
        double mx = dx(event.x());
        double my = dy(event.y());
        if (open == null) {
            if (mx >= LEFT && mx <= LEFT + PAGE_W && my >= LIST_Y && my < BOTTOM) {
                int idx = (int) ((my - LIST_Y) / ROW) + listScroll;
                if (idx >= 0 && idx < rows.size()) {
                    Row row = rows.get(idx);
                    if (row.category != null) {
                        collapsed.put(row.category, !collapsed.getOrDefault(row.category, false));
                        rebuildRows();
                    } else if (row.entry != null) {
                        openEntry(row.entry.id());
                    }
                    return true;
                }
            }
            return super.mouseClicked(event, isDoubleClick);
        }
        if (my >= TOP - 2 && my <= TOP + 9 && mx >= LEFT && mx <= LEFT + 40) {
            open = null;
            return true;
        }
        if (my >= H - 15 && my <= H - 4) {
            if (mx >= RIGHT && mx <= RIGHT + 10) {
                step(-1);
                return true;
            }
            if (mx >= W - PAD - 10 && mx <= W - PAD) {
                step(1);
                return true;
            }
        }
        int ty = PLATE_Y + PLATE + 4;
        if (my >= ty - 1 && my <= ty + 9) {
            if (mx >= LEFT + PAGE_W - 40) {
                showLive = !showLive;
                showSignature = false;
                return true;
            }
            if (mx >= LEFT + PAGE_W - 82) {
                showSignature = !showSignature;
                showLive = false;
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void openEntry(Identifier id) {
        open = id;
        plateScroll = 0;
        notesScroll = 0;
        showSignature = false;
        showLive = false;
    }

    /** Leafs to the previous or next page in index order. */
    private void step(int direction) {
        List<Identifier> order = rows.stream().filter(r -> r.entry != null).map(r -> r.entry.id()).toList();
        int at = order.indexOf(open);
        if (at >= 0 && !order.isEmpty()) {
            openEntry(order.get(Math.floorMod(at + direction, order.size())));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double mx = dx(mouseX);
        int delta = -(int) Math.signum(scrollY);
        if (open == null) {
            listScroll = Math.clamp(listScroll + delta, 0, listScrollMax);
        } else if (mx < SPINE) {
            plateScroll = Math.clamp(plateScroll + delta * 2, 0, plateScrollMax);
        } else {
            notesScroll = Math.clamp(notesScroll + delta * 2, 0, notesScrollMax);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(@Nullable BestiaryCategory category, @Nullable BestiaryEntry entry) {}

    /** For the research summary tooltip and tests: the open entry's research, or null on the index. */
    @Nullable ResearchProgress openResearch() {
        BestiaryEntry entry = open == null ? null : BestiaryEntryRegistry.clientGet(open);
        return entry == null ? null : ResearchRewards.progress(entry, data(), JournalBook.clientFacts(entry.id()));
    }
}
