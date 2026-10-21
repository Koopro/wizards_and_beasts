package at.koopro.wizardsandbeasts.admin.player;

import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Item;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.education.EducationService;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialResourceManager;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfState;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.BuildTier;
import at.koopro.wizardsandbeasts.skill.build.Builds;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.keyOf;
import static at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.keyed;
import static at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.money;
import static at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.text;

/**
 * The Players section's facets added after the first eight: conditions, wand, profession and creatures. Same rule as
 * {@link PlayerAdminService}: every value is read from the owning system at the moment of asking; nothing is copied.
 */
@NullMarked
final class PlayerFacets {

    private static final String FACT = "admin.wizards_and_beasts.player_fact.";

    private PlayerFacets() {}

    /** Lycanthropy / Obscurus: which one (a player carries one at most) and its live state. */
    static void conditions(ServerPlayer target, List<AdminSpellFact> facts) {
        PlayerHeritageData data = HeritageAPI.getData(target);
        ConditionOrigin origin = data.getCondition();
        if (origin == null) {
            facts.add(keyed("condition", FACT + "none"));
            return;
        }
        facts.add(keyed("condition", origin.condition().getTranslationKey()));
        facts.add(keyed("condition_origin", origin.getTranslationKey()));
        facts.add(text("form_state", data.getTransformationState().name().toLowerCase(Locale.ROOT)));
        facts.add(text("active_form", data.getActiveFormId() == null || data.getActiveFormId().isEmpty()
                ? "—" : data.getActiveFormId()));
        if (!origin.traits().isEmpty()) {
            facts.add(text("condition_traits", String.join(", ", origin.traits())));
        }
        long dayTime = target.level().getDayTime();
        if (WerewolfRules.isWerewolf(data)) {
            facts.add(text("moon_phase", WerewolfRules.moonPhase(dayTime) + (WerewolfRules.moonPhase(dayTime)
                    == WerewolfRules.FULL_MOON ? " (full)" : "")));
            facts.add(keyed("transformed", FACT + (WerewolfRules.isTransformed(data) ? "yes" : "no")));
            facts.add(keyed("loss_of_control", FACT + (WerewolfState.isLossOfControl(data) ? "yes" : "no")));
            facts.add(keyed("feral", FACT + (WerewolfRules.isFeral(target) ? "yes" : "no")));
            facts.add(keyed("wolfsbane", FACT + (WerewolfRules.hasWolfsbane(target) ? "yes" : "no")));
            facts.add(text("moon_exposure", Integer.toString(WerewolfState.getExposure(data))));
            long gameTime = target.level().getGameTime();
            facts.add(text("next_transform", WerewolfState.hasPendingTransform(data)
                    ? Math.max(0, (WerewolfState.getTransformAtTick(data) - gameTime) / 20) + "s" : "—"));
        }
        if (ObscurialRules.isObscurial(data)) {
            facts.add(keyed("dark_form", FACT + (ObscurialRules.isDarkForm(data) ? "yes" : "no")));
            facts.add(text("obscurial_drain", String.format(Locale.ROOT, "%.1f", ObscurialResourceManager.getDrain(target))));
            facts.add(text("obscurial_charge", String.format(Locale.ROOT, "%.1f", ObscurialResourceManager.getCharge(target))));
            facts.add(text("obscurial_stress", String.format(Locale.ROOT, "%.1f", ObscurialResourceManager.getStress(target))));
            long lockout = ObscurialResourceManager.getLockoutUntilTick(target) - target.level().getGameTime();
            facts.add(text("obscurial_lockout", lockout > 0 ? (lockout / 20) + "s" : "—"));
        }
    }

    /** Every wand the player carries, and the one that answers to them first. */
    static void wand(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        UUID self = target.getUUID();
        ItemStack own = ItemStack.EMPTY;
        int carried = 0;
        for (int slot = 0; slot < target.getInventory().getContainerSize(); slot++) {
            ItemStack stack = target.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof WandItem)) {
                continue;
            }
            carried++;
            Optional<UUID> master = WandComponents.getMaster(stack);
            boolean mine = master.isPresent() && master.get().equals(self);
            if (mine && own.isEmpty()) {
                own = stack;
            }
            items.add(new Item("slot_" + slot, stack.getHoverName().getString(), false,
                    describe(stack) + " · " + WandAllegianceService.stateFor(self, stack).name().toLowerCase(Locale.ROOT)
                            + (mine ? " · master" : master.isPresent() ? " · answers to another" : ""), false));
        }
        facts.add(text("wands_carried", Integer.toString(carried)));
        if (own.isEmpty()) {
            facts.add(keyed("own_wand", FACT + "none"));
            return;
        }
        facts.add(text("own_wand", own.getHoverName().getString()));
        facts.add(text("wand_wood", path(WandComponents.getWood(own))));
        facts.add(text("wand_core", path(WandComponents.getCore(own))));
        Float length = WandComponents.getLength(own);
        facts.add(text("wand_length", length == null ? "—" : String.format(Locale.ROOT, "%.1f\"", length)));
        facts.add(text("wand_flexibility", WandComponents.getFlexibility(own) == null ? "—"
                : WandComponents.getFlexibility(own).getSerializedName()));
        facts.add(text("wand_bond", WandAllegianceService.stateFor(self, own).name().toLowerCase(Locale.ROOT)));
        facts.add(text("wand_allegiance", String.format(Locale.ROOT, "%.2f", WandComponents.getAllegianceScore(own))));
        facts.add(text("wand_integrity", String.format(Locale.ROOT, "%.0f%%", WandComponents.getIntegrity(own) * 100f)));
        facts.add(text("wand_corruption", String.format(Locale.ROOT, "%.0f%%", WandComponents.getCorruption(own) * 100f)));
        facts.add(text("wand_casts", Integer.toString(WandComponents.getCastCount(own))));
        facts.add(keyed("elder_wand", FACT + (WandAllegianceService.isElderWand(own) ? "yes" : "no")));
    }

    private static String describe(ItemStack wand) {
        Float length = WandComponents.getLength(wand);
        return path(WandComponents.getWood(wand)) + " / " + path(WandComponents.getCore(wand))
                + (length == null ? "" : String.format(Locale.ROOT, " / %.1f\"", length));
    }

    private static String path(@org.jspecify.annotations.Nullable Identifier id) {
        return id == null ? "—" : id.getPath();
    }

    /** Career, rank and pay; vocation; build; exam results. */
    static void profession(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        PlayerProfessionData profession = target.getData(ModAttachments.PROFESSION_DATA.get());
        facts.add(profession.profession() == null ? keyed("profession", FACT + "none")
                : keyed("profession", profession.profession().translationKey()));
        CareerView career = CareerProgression.view(target);
        if (career.state() != CareerView.NO_CAREER) {
            facts.add(keyed("career_rank", keyOf(career.rank())));
            if (career.state() == CareerView.TRACK) {
                facts.add(text("career_xp", career.xp() + " / " + career.nextXp() + " (rank " + (career.rankIndex() + 1)
                        + " of " + career.rankCount() + ")"));
            }
            facts.add(text("career_wage", money(career.wageKnuts())));
            facts.add(text("career_contracts", career.active().size() + " active · " + career.offers().size() + " offered"));
            for (CareerView.GoalLine goal : career.goals()) {
                items.add(new Item("goal", keyOf(goal.text()), true, goal.progress() + " / " + goal.target()
                        + (goal.met() ? " ✓" : ""), false));
            }
        }
        Optional<Identifier> vocation = VocationHelper.getPrimary(target);
        facts.add(text("vocation", vocation.map(Identifier::getPath).orElse("—")));
        BuildProfile build = Builds.profileOf(target);
        facts.add(build.current() == null ? keyed("build", FACT + "none")
                : keyed("build", build.current().nameKey()));
        facts.add(text("build_status", build.status().name().toLowerCase(Locale.ROOT)));
        for (BuildProfile.PathwayProgress pathway : build.pathways()) {
            StringBuilder tiers = new StringBuilder();
            for (BuildTier tier : BuildTier.values()) {
                tiers.append(tiers.isEmpty() ? "" : " · ").append(tier.name().toLowerCase(Locale.ROOT)).append(' ')
                        .append(pathway.count(tier)).append('/').append(pathway.size(tier));
            }
            items.add(new Item(pathway.archetype().id().toString(), pathway.archetype().nameKey(), true,
                    "score " + pathway.score() + " · " + tiers + (pathway.capstoneHeld() ? " · capstone" : ""), false));
        }
        PlayerOWLData owl = target.getData(ModAttachments.OWL_DATA.get());
        facts.add(text("owl_sittings", owl.sittings() + " O.W.L. · " + owl.newtSittings() + " N.E.W.T."));
        grades(items, "O.W.L.", owl.grades());
        grades(items, "N.E.W.T.", owl.newtGrades());
        var education = EducationService.view(target);
        facts.add(keyed("exam_sitting", FACT + (education.sitting() == null ? "no" : "yes")));
        facts.add(keyed("may_sit", FACT + (education.maySitOwl() || education.maySitNewt() ? "yes" : "no")));
    }

    private static void grades(List<Item> items, String level, Map<OWLSubject, OWLGrade> grades) {
        grades.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                items.add(new Item(level + "_" + entry.getKey().name().toLowerCase(Locale.ROOT),
                        entry.getKey().translationKey(), true, level + " · " + entry.getValue().name(), false)));
    }

    /** The player's companions, alive or remembered, from their roster (works for creatures in unloaded chunks too). */
    static void creatures(AdminContext actor, ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        CompanionRoster roster = target.getData(ModAttachments.COMPANION_ROSTER.get());
        long alive = roster.entries().values().stream().filter(CompanionRoster.Entry::alive).count();
        facts.add(text("companions", alive + " alive · " + (roster.entries().size() - alive) + " remembered"));
        facts.add(text("trusted_hostile", roster.trusted().size() + " trusted · " + roster.hostile().size() + " hostile"));
        boolean positions = PlayerAdminService.maySeeLocation(actor);
        roster.entries().forEach((id, entry) -> items.add(new Item(id.toString(),
                (entry.name().isEmpty() ? entry.species().getPath() : entry.name() + " (" + entry.species().getPath() + ")"),
                false, entry.stage().name().toLowerCase(Locale.ROOT) + " · bond " + entry.bond() + "/" + entry.maxBond()
                        + String.format(Locale.ROOT, " · ♥ %.0f/%.0f", entry.health(), entry.maxHealth())
                        + " · " + entry.mode().name().toLowerCase(Locale.ROOT)
                        + (entry.alive() ? "" : " · gone")
                        + (positions ? " · " + entry.dimension() + " " + entry.x() + " " + entry.y() + " " + entry.z() : ""),
                false)));
    }
}
