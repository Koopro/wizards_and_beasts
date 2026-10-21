package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.client.bestiary.ClientBestiaryCache;
import at.koopro.wizardsandbeasts.client.career.ClientCareerState;
import at.koopro.wizardsandbeasts.client.companion.ClientCompanionState;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.education.ClientEducationState;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.character.SheetCaches;
import at.koopro.wizardsandbeasts.client.gui.character.SheetTab;
import at.koopro.wizardsandbeasts.client.gui.character.SheetWand;
import at.koopro.wizardsandbeasts.client.gui.character.widget.ActiveEffectsWidget;
import at.koopro.wizardsandbeasts.client.gui.character.widget.HeritageBlockWidget;
import at.koopro.wizardsandbeasts.client.gui.character.widget.ProgressCard;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.location.ClientPlaceStandingState;
import at.koopro.wizardsandbeasts.client.ministry.state.ClientMinistryRecordState;
import at.koopro.wizardsandbeasts.client.skill.state.ClientSkillDataState;
import at.koopro.wizardsandbeasts.client.standing.state.ClientStandingState;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.education.EducationView;
import at.koopro.wizardsandbeasts.location.PlaceStanding;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.career.CareerActionC2SPayload;
import at.koopro.wizardsandbeasts.network.companion.CompanionActionC2SPayload;
import at.koopro.wizardsandbeasts.network.education.EducationActionC2SPayload;
import at.koopro.wizardsandbeasts.network.location.PlaceStandingsC2SPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.BuildTier;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The sheet's first page: who this wizard is, and what they are becoming.
 *
 * <p>Top to bottom: the identity facts (heritage, lineage, condition, patronus), where they stand with the Ministry
 * and the bank, one card per road they are walking — build, magic, wand, profession, education, reputation,
 * creatures — then the effects on them now and what their heritage means in play. Each card is a summary and a
 * link: it opens the page that holds the detail.
 *
 * <p>Every number comes from state the server synced or a view it built; the cards only choose which numbers to put
 * side by side. They are rebuilt a few times a second, not every frame, and the expensive derivations underneath
 * them ({@link SheetCaches}) only when a sync lands. A card whose system is switched off, or has nothing yet, says so
 * and says how that changes — it never fills itself with a placeholder number.
 */
@NullMarked
public final class IdentityTab extends ScrollingTab {

    private static final UiSurface S = UiSurface.PAGE;
    private static final String KEY = "gui.wizards_and_beasts.character_sheet.identity.";
    private static final String CARD = KEY + "card.";
    /** Frames between card rebuilds: about six a second, which is faster than any of their sources changes. */
    private static final int REBUILD_FRAMES = 10;
    private static final int CARD_GAP = 3;

    private final Consumer<SheetTab> navigator;
    private final HeritageProfileSection heritage = new HeritageProfileSection();
    private List<ProgressCard.Content> cards = List.of();
    private int framesSinceBuild = REBUILD_FRAMES;

    /** @param navigator opens another page of the sheet, for the cards */
    public IdentityTab(Consumer<SheetTab> navigator) {
        this.navigator = navigator;
    }

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.identity";
    }

    /**
     * Asks for every server-built view the cards summarise. The pages that own them ask again when they are opened;
     * each request is throttled on the server, so opening the sheet costs a handful of small packets, once.
     */
    @Override
    public void onShown() {
        resetScroll();
        framesSinceBuild = REBUILD_FRAMES;
        send(new CareerActionC2SPayload(CareerActionC2SPayload.Action.VIEW, ""));
        send(EducationActionC2SPayload.of(EducationActionC2SPayload.Action.VIEW));
        send(new CompanionActionC2SPayload(new UUID(0, 0), CompanionActionC2SPayload.Action.REFRESH, CompanionCommand.FOLLOW));
        send(new PlaceStandingsC2SPayload());
        heritage.request();
    }

    @Override
    protected int drawContent(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) {
            return y;
        }
        if (++framesSinceBuild >= REBUILD_FRAMES) {
            framesSinceBuild = 0;
            cards = buildCards(player);
        }

        // ── who ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.who"), null);
        y = HeritageBlockWidget.draw(g, x, y, w);
        y = standingRows(g, font, x, y, w);
        y += WizardsMetrics.SPACE_M;

        // ── what they are becoming ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.becoming"),
                Component.translatable(KEY + "section.becoming.caption"));
        for (ProgressCard.Content card : cards) {
            boolean hover = hovered(x, y, w, ProgressCard.HEIGHT, mouseX, mouseY);
            ProgressCard.draw(g, font, x, y, w, card, hover);
            if (hover) {
                tooltip(card.tooltip());
            }
            SheetTab target = card.target();
            clickable(x, y, w, ProgressCard.HEIGHT, () -> navigator.accept(target));
            y += ProgressCard.HEIGHT + CARD_GAP;
        }
        y += WizardsMetrics.SPACE_M;

        // ── right now ──
        y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable("gui.wizards_and_beasts.character_sheet.effects"),
                null);
        y = ActiveEffectsWidget.draw(g, x, y, w);
        y += WizardsMetrics.SPACE_M;

        // ── what their people mean in play ──
        if (ModuleManager.isEnabled(Module.HERITAGE)) {
            y = Ui.sectionHeader(g, font, S, x, y, w, Component.translatable(KEY + "section.heritage"), null);
            y = heritage.draw(g, font, x, y, w);
        }
        return y;
    }

    /** The Ministry's view of the wizard and what is in the vault: two facts a wizard is known by. */
    private static int standingRows(GuiGraphics g, Font font, int x, int y, int w) {
        if (ClientMinistryRecordState.traceActive()) {
            var band = ClientMinistryRecordState.get().wantedLevel();
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "ministry"), band.displayName(),
                    RecordTab.bandColor(band));
        } else {
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "ministry"),
                    Component.translatable(KEY + "ministry.off"), S.ink3());
        }
        if (ModuleManager.isEnabled(Module.GRINGOTTS)) {
            var vault = ClientVaultDataState.get();
            long knuts = CurrencyHelper.toKnuts(vault.getGalleons(), vault.getSickles(), vault.getKnuts());
            y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "vault"),
                    Component.literal(CurrencyHelper.formatFromKnuts(knuts)));
        }
        return y;
    }

    // ── cards ─────────────────────────────────────────────────────────────

    private List<ProgressCard.Content> buildCards(LocalPlayer player) {
        List<ProgressCard.Content> out = new ArrayList<>(7);
        out.add(buildCard());
        out.add(magicCard());
        out.add(wandCard(player));
        out.add(professionCard());
        out.add(educationCard());
        out.add(reputationCard());
        out.add(creaturesCard());
        return out;
    }

    private static ProgressCard.Content buildCard() {
        SheetTab tab = SheetTab.SKILLS;
        Component title = Component.translatable(CARD + "build");
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            return off(tab, title);
        }
        BuildProfile profile = SheetCaches.build();
        int points = ClientSkillDataState.get().getSkillPoints();
        Component value = profile.current() == null
                ? Component.translatable("gui.wizards_and_beasts.character_sheet.build.status.undecided")
                : Component.translatable(profile.current().nameKey());
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        if (profile.pathways().isEmpty()) {
            tip.add(Component.translatable("gui.wizards_and_beasts.character_sheet.build.empty"));
            tip.add(points(points));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, value, S.ink2(),
                    points > 0 ? points(points) : Component.translatable(CARD + "build.none"), -1.0f, -1.0f, tip);
        }
        BuildProfile.PathwayProgress top = profile.pathways().getFirst();
        int held = 0;
        int size = 0;
        for (BuildTier tier : BuildTier.values()) {
            held += top.count(tier);
            size += top.size(tier);
        }
        Component road = Component.translatable(top.archetype().nameKey());
        Component detail = top.capstoneHeld()
                ? Component.translatable(CARD + "build.mastered", held, size)
                : Component.translatable(CARD + "build.road", road, held, size);
        for (int i = 0; i < Math.min(3, profile.pathways().size()); i++) {
            BuildProfile.PathwayProgress p = profile.pathways().get(i);
            tip.add(Component.translatable(p.archetype().nameKey()).append(": ").append(Component.translatable(
                    "gui.wizards_and_beasts.character_sheet.build.tiers",
                    p.count(BuildTier.EARLY), p.size(BuildTier.EARLY), p.count(BuildTier.MID), p.size(BuildTier.MID),
                    p.count(BuildTier.ADVANCED), p.size(BuildTier.ADVANCED))));
        }
        tip.add(points(points));
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, value, profile.current() == null ? S.ink2() : S.heading(), detail,
                size == 0 ? -1.0f : held / (float) size, -1.0f, tip);
    }

    private static Component points(int points) {
        return Component.translatable(CARD + "build.points", points);
    }

    private static ProgressCard.Content magicCard() {
        SheetTab tab = SheetTab.MAGIC;
        Component title = Component.translatable(CARD + "magic");
        SheetCaches.Spellbook book = SheetCaches.spellbook();
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        if (book.all().isEmpty()) {
            tip.add(Component.translatable(CARD + "magic.none"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, Component.translatable(CARD + "magic.known", 0), S.ink3(),
                    Component.translatable(CARD + "magic.none"), -1.0f, -1.0f, tip);
        }
        Component detail = Component.translatable(CARD + "magic.tiers", book.count(Proficiency.MASTERED),
                book.count(Proficiency.PROFICIENT), book.count(Proficiency.NOVICE));
        tip.add(detail);
        tip.add(Component.translatable(CARD + "magic.mastery", Math.round(book.mastery() * 100)));
        SheetCaches.KnownSpell closest = book.closestToNextTier();
        if (closest != null) {
            tip.add(Component.translatable(CARD + "magic.closest", closest.name(), closest.nextAt() - closest.hits(),
                    tierName(closest.proficiency() == Proficiency.NOVICE ? Proficiency.PROFICIENT : Proficiency.MASTERED)));
        }
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, Component.translatable(CARD + "magic.known", book.all().size()),
                S.ink(), detail, book.mastery(), -1.0f, tip);
    }

    private static ProgressCard.Content wandCard(LocalPlayer player) {
        SheetTab tab = SheetTab.WAND;
        Component title = Component.translatable(CARD + "wand");
        SheetWand.Found found = SheetWand.find(player);
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        if (!found.present()) {
            tip.add(Component.translatable(CARD + "wand.none"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, Component.literal("—"), S.ink3(),
                    Component.translatable(CARD + "wand.none"), -1.0f, -1.0f, tip);
        }
        var stack = found.stack();
        var registries = player.level().registryAccess();
        Component detail = Component.empty().append(WandLoreNames.wood(registries, WandComponents.getWood(stack)))
                .append(" · ").append(WandLoreNames.core(registries, WandComponents.getCore(stack)));
        tip.add(stack.getHoverName());
        tip.add(detail);
        if (!WandAllegianceService.answersToSomeone(stack)) {
            tip.add(Component.translatable("wandcraft.eligibility.reason.not_attuned"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, Component.translatable(WandTab.STATE + "unchosen"), S.ink2(),
                    detail, -1.0f, -1.0f, tip);
        }
        WandBondState state = WandAllegianceService.stateFor(player.getUUID(), stack);
        float bond = state.isMaster() ? WandComponents.getAllegianceScore(stack) : 0.0f;
        tip.add(WandTab.bondSentence(state));
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, WandTab.stateName(state), WandTab.stateColour(state), detail,
                bond, WandTab.nextThreshold(state), tip);
    }

    private static ProgressCard.Content professionCard() {
        SheetTab tab = SheetTab.PROFESSION;
        Component title = Component.translatable(CARD + "profession");
        if (!ModuleManager.isEnabled(Module.OWLS)) {
            return off(tab, title);
        }
        List<Component> tip = new ArrayList<>();
        if (ClientCareerState.version() == 0) {
            return asking(tab, title);
        }
        CareerView view = ClientCareerState.get();
        if (view.state() == CareerView.NO_CAREER) {
            tip.add(title);
            tip.add(Component.translatable("gui.wizards_and_beasts.character_sheet.career.none_hint"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, Component.translatable(CARD + "profession.none"), S.ink3(),
                    Component.translatable("gui.wizards_and_beasts.character_sheet.career.none_hint"), -1.0f, -1.0f, tip);
        }
        tip.add(view.profession());
        Component wage = Component.translatable(CARD + "profession.wage", CurrencyHelper.formatFromKnuts(view.wageKnuts()));
        if (view.state() == CareerView.FLAT_CAREER) {
            tip.add(wage);
            tip.add(Component.translatable("gui.wizards_and_beasts.character_sheet.career.flat"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, view.profession().copy(), Component.empty(), S.ink(), wage, -1.0f, -1.0f, tip);
        }
        boolean top = view.nextXp() < 0;
        Component detail = top ? view.rank().copy()
                : Component.translatable(CARD + "profession.next", view.rank(), view.nextRank());
        tip.add(detail);
        tip.add(wage);
        for (CareerView.GoalLine goal : view.goals()) {
            tip.add(Component.literal(goal.met() ? "✔ " : "• ").append(goal.text())
                    .append(" " + Math.min(goal.progress(), goal.target()) + "/" + goal.target()));
        }
        tip.add(open(tab));
        float fraction = top ? 1.0f : view.xp() / (float) Math.max(1, view.nextXp());
        return new ProgressCard.Content(tab, view.profession().copy(),
                Component.translatable(CARD + "profession.rank", view.rankIndex() + 1, view.rankCount()),
                top ? S.good() : S.ink(), detail, fraction, -1.0f, tip);
    }

    private static ProgressCard.Content educationCard() {
        SheetTab tab = SheetTab.EDUCATION;
        Component title = Component.translatable(CARD + "education");
        if (!ModuleManager.isEnabled(Module.OWLS)) {
            return off(tab, title);
        }
        if (ClientEducationState.version() == 0) {
            return asking(tab, title);
        }
        EducationView view = ClientEducationState.get();
        if (!view.enabled()) {
            return off(tab, title);
        }
        int owls = 0;
        int newts = 0;
        float coursework = 0.0f;
        for (EducationView.SubjectLine line : view.subjects()) {
            if (line.owlRecord() != null && line.owlRecord().passing) {
                owls++;
            }
            if (line.newtRecord() != null && line.newtRecord().passing) {
                newts++;
            }
            int outstanding = line.owl()[2];
            coursework += outstanding <= 0 ? 1.0f : Math.min(1.0f, line.score() / (float) outstanding);
        }
        float fraction = view.subjects().isEmpty() ? -1.0f : coursework / view.subjects().size();
        Component detail = view.sitting() != null
                ? Component.translatable(CARD + "education.sitting")
                : Component.translatable(CARD + "education.newts", newts);
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        tip.add(Component.translatable(CARD + "education.owls", owls));
        tip.add(Component.translatable(CARD + "education.newts", newts));
        if (fraction >= 0.0f) {
            tip.add(Component.translatable(CARD + "education.coursework", Math.round(fraction * 100)));
        }
        for (Component next : view.next()) {
            tip.add(Component.literal("• ").append(next));
        }
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, Component.translatable(CARD + "education.owls", owls),
                owls > 0 ? S.good() : S.ink2(), detail, fraction, -1.0f, tip);
    }

    private static ProgressCard.Content reputationCard() {
        SheetTab tab = SheetTab.REPUTATION;
        Component title = Component.translatable(CARD + "reputation");
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        Component value;
        int valueColour;
        if (ClientMinistryRecordState.traceActive()) {
            var band = ClientMinistryRecordState.get().wantedLevel();
            value = band.displayName();
            valueColour = RecordTab.bandColor(band);
            tip.add(Component.translatable(KEY + "ministry").append(": ").append(value));
        } else {
            value = Component.literal("—");
            valueColour = S.ink3();
        }
        Component detail = Component.translatable(CARD + "reputation.none");
        if (ClientStandingState.hasData()) {
            StandingAxis strongest = null;
            float best = -1.0f;
            for (StandingAxis axis : StandingAxis.values()) {
                float f = Math.abs(ClientStandingState.fractionOf(axis));
                tip.add(axis.displayName().copy().append(": ").append(axis.bandName(ClientStandingState.bandOf(axis))));
                if (f > best) {
                    best = f;
                    strongest = axis;
                }
            }
            if (strongest != null && best > 0.0f) {
                detail = strongest.displayName().copy().append(": ")
                        .append(strongest.bandName(ClientStandingState.bandOf(strongest)));
            }
        }
        int known = 0;
        for (PlaceStanding place : ClientPlaceStandingState.get()) {
            if (place.dealtWith()) {
                known++;
                tip.add(place.name().copy().append(": ").append(place.tier()));
            }
        }
        if (known > 0) {
            tip.add(Component.translatable(CARD + "reputation.places", known));
        }
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, value, valueColour, detail, -1.0f, -1.0f, tip);
    }

    private static ProgressCard.Content creaturesCard() {
        SheetTab tab = SheetTab.CREATURES;
        Component title = Component.translatable(CARD + "creatures");
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return off(tab, title);
        }
        CompanionRoster roster = ClientCompanionState.get();
        CompanionRoster.Entry best = null;
        int living = 0;
        for (CompanionRoster.Entry entry : roster.entries().values()) {
            if (!entry.alive()) {
                continue;
            }
            living++;
            if (best == null || entry.bond() / (float) Math.max(1, entry.maxBond())
                    > best.bond() / (float) Math.max(1, best.maxBond())) {
                best = entry;
            }
        }
        List<Component> tip = new ArrayList<>();
        tip.add(title);
        Map<DiscoveryTier, Integer> journal = new EnumMap<>(DiscoveryTier.class);
        for (DiscoveryTier tier : ClientBestiaryCache.get().tiers().values()) {
            journal.merge(tier, 1, Integer::sum);
        }
        tip.add(Component.translatable(CARD + "creatures.journal", journal.getOrDefault(DiscoveryTier.KNOWN, 0),
                journal.values().stream().mapToInt(Integer::intValue).sum()));
        if (best == null) {
            tip.add(Component.translatable("gui.wizards_and_beasts.character_sheet.beasts.none_hint"));
            tip.add(open(tab));
            return new ProgressCard.Content(tab, title, Component.translatable(CARD + "creatures.count", 0), S.ink3(),
                    Component.translatable(CARD + "creatures.none"), -1.0f, -1.0f, tip);
        }
        Component bestName = best.name().isEmpty()
                ? Component.translatable(BuiltInRegistries.ENTITY_TYPE.getValue(best.species()).getDescriptionId())
                : Component.literal(best.name());
        MutableComponent detail = bestName.copy().append(" · ").append(Component.translatable(best.stage().key()));
        tip.add(detail);
        tip.add(open(tab));
        return new ProgressCard.Content(tab, title, Component.translatable(CARD + "creatures.count", living), S.ink(),
                detail, best.bond() / (float) Math.max(1, best.maxBond()), -1.0f, tip);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /** A card for a system the server has switched off: named, dimmed, and saying so. */
    private static ProgressCard.Content off(SheetTab tab, Component title) {
        Component why = Component.translatable(CARD + "off");
        return new ProgressCard.Content(tab, title, Component.literal("—"), S.ink3(), why, -1.0f, -1.0f,
                List.of(title, UiTooltips.disabledReason(why)));
    }

    /** A card whose view the server has not answered with yet. */
    private static ProgressCard.Content asking(SheetTab tab, Component title) {
        Component why = Component.translatable(CARD + "asking");
        return new ProgressCard.Content(tab, title, Component.empty(), S.ink3(), why, -1.0f, -1.0f, List.of(title, why));
    }

    private static Component open(SheetTab tab) {
        return Component.translatable(CARD + "open", tab.label());
    }

    private static Component tierName(Proficiency tier) {
        return Component.translatable("gui.wizards_and_beasts.character_sheet.magic.tier."
                + tier.name().toLowerCase(Locale.ROOT));
    }

    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }
}
