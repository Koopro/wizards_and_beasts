package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDependencies;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.network.admin.AdminRequestLimiter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * Diagnostics section reports. Every number is read from a real counter at the moment of asking; where the game keeps
 * none, the report says so instead of estimating.
 *
 * <ul>
 *   <li>{@code diag_network}: vanilla's per-connection latency and packet rates, plus the Control Center's own request
 *       limiter (admitted / dropped since start).</li>
 *   <li>{@code diag_registry}: every registry, static and data-pack, with how many entries carry the mod's namespace;
 *       the main deferred registers are compared against what actually landed.</li>
 *   <li>{@code diag_data}: what every data-pack loader holds now. An empty table is flagged: the mod ships data for
 *       each, so empty means the pack failed to load or was removed.</li>
 *   <li>{@code diag_modules}: module states and every dependency edge, flagged where an enabled module needs one that
 *       is off.</li>
 * </ul>
 */
@NullMarked
final class DiagnosticReports {

    private DiagnosticReports() {}

    static void registerAll() {
        AdminReports.register(AdminReport.of("diag_network", AdminCategory.DIAGNOSTICS, DiagnosticReports::network));
        AdminReports.register(AdminReport.of("diag_registry", AdminCategory.DIAGNOSTICS, DiagnosticReports::registry));
        AdminReports.register(AdminReport.of("diag_data", AdminCategory.DIAGNOSTICS, DiagnosticReports::data));
        AdminReports.register(AdminReport.of("diag_modules", AdminCategory.DIAGNOSTICS, DiagnosticReports::modules));
    }

    private static void network(AdminReport.Request request, ReportBuilder out) {
        MinecraftServer server = request.server();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        out.section("totals");
        out.row("players", players.size() + " / " + server.getMaxPlayers());
        float sent = 0;
        float received = 0;
        long latency = 0;
        for (ServerPlayer player : players) {
            sent += player.connection.getConnection().getAverageSentPackets();
            received += player.connection.getConnection().getAverageReceivedPackets();
            latency += player.connection.latency();
        }
        out.row("packets_sent", String.format(Locale.ROOT, "%.1f/s", sent));
        out.row("packets_received", String.format(Locale.ROOT, "%.1f/s", received));
        out.row("latency_avg", players.isEmpty() ? "—" : (latency / players.size()) + " ms");
        out.row("admin_requests", AdminRequestLimiter.admittedCount());
        out.row("admin_dropped", AdminRequestLimiter.droppedCount(),
                AdminRequestLimiter.droppedCount() > 0 ? Tone.WARN : Tone.GOOD);
        out.section("connections");
        players.stream().sorted(Comparator.comparingInt((ServerPlayer p) -> p.connection.latency()).reversed())
                .forEach(player -> {
                    int ms = player.connection.latency();
                    out.literal(player.getName().getString(), String.format(Locale.ROOT, "%d ms · ↑%.1f/s · ↓%.1f/s", ms,
                                    player.connection.getConnection().getAverageSentPackets(),
                                    player.connection.getConnection().getAverageReceivedPackets()),
                            ms > 300 ? Tone.BAD : ms > 150 ? Tone.WARN : Tone.NORMAL);
                });
        out.note("network_note");
    }

    private static void registry(AdminReport.Request request, ReportBuilder out) {
        RegistryAccess access = request.server().registryAccess();
        out.section("deferred");
        deferred(out, "items", at.koopro.wizardsandbeasts.registry.ModItems.ITEMS, access, net.minecraft.core.registries.Registries.ITEM);
        deferred(out, "blocks", at.koopro.wizardsandbeasts.registry.ModBlocks.BLOCKS, access, net.minecraft.core.registries.Registries.BLOCK);
        deferred(out, "entities", at.koopro.wizardsandbeasts.registry.ModEntities.ENTITY_TYPES, access,
                net.minecraft.core.registries.Registries.ENTITY_TYPE);
        deferred(out, "block_entities", at.koopro.wizardsandbeasts.registry.ModBlockEntities.BLOCK_ENTITY_TYPES, access,
                net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE);
        deferred(out, "effects", at.koopro.wizardsandbeasts.effect.ModEffects.MOB_EFFECTS, access,
                net.minecraft.core.registries.Registries.MOB_EFFECT);
        deferred(out, "sounds", at.koopro.wizardsandbeasts.registry.ModSounds.SOUND_EVENTS, access,
                net.minecraft.core.registries.Registries.SOUND_EVENT);
        out.section("all_registries");
        record Row(String name, int mod, int total) {}
        List<Row> rows = new ArrayList<>();
        access.registries().forEach(entry -> {
            Registry<?> registry = entry.value();
            int mod = (int) registry.keySet().stream()
                    .filter(id -> id.getNamespace().equals(WizardsAndBeastsMod.MODID)).count();
            if (mod > 0) {
                rows.add(new Row(entry.key().identifier().toString(), mod, registry.size()));
            }
        });
        rows.sort(Comparator.comparing(Row::name));
        for (Row row : rows) {
            out.literal(row.name(), row.mod() + " of " + row.total());
        }
    }

    private static void deferred(ReportBuilder out, String name, DeferredRegister<?> register, RegistryAccess access,
                                 net.minecraft.resources.ResourceKey<? extends Registry<?>> key) {
        int declared = register.getEntries().size();
        int live = access.lookup(key).map(registry -> (int) registry.keySet().stream()
                .filter(id -> id.getNamespace().equals(WizardsAndBeastsMod.MODID)).count()).orElse(-1);
        out.row(name, declared + " declared · " + (live < 0 ? "?" : Integer.toString(live)) + " live",
                live == declared ? Tone.GOOD : Tone.WARN);
    }

    private static void data(AdminReport.Request request, ReportBuilder out) {
        out.section("loaders");
        table(out, "spells", () -> at.koopro.wizardsandbeasts.spell.def.SpellReloadListener.loaded().size());
        table(out, "brews", at.koopro.wizardsandbeasts.brew.Brews::count);
        table(out, "brewing_recipes", at.koopro.wizardsandbeasts.brew.BrewingRecipes::count);
        table(out, "creatures", at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry::size);
        table(out, "bond_profiles", at.koopro.wizardsandbeasts.creature.bond.BondProfileRegistry::size);
        table(out, "ecology_profiles", () -> at.koopro.wizardsandbeasts.creature.ecology.EcologyProfileRegistry.snapshot().size());
        table(out, "companion_traits", () -> at.koopro.wizardsandbeasts.creature.companion.CompanionData.traits().size());
        table(out, "bestiary", () -> at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry.getAll().size());
        table(out, "harvest_rules", at.koopro.wizardsandbeasts.bestiary.harvest.HarvestTable::count);
        table(out, "skill_nodes", at.koopro.wizardsandbeasts.skill.SkillTrees::count);
        table(out, "vocations", () -> at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry.all().size());
        table(out, "abilities", at.koopro.wizardsandbeasts.ability.def.AbilityDefinitionRegistry::size);
        table(out, "animagus_forms", () -> at.koopro.wizardsandbeasts.animagus.AnimagusFormRegistry.getAll().size());
        table(out, "heritage_appearances", () -> at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearanceRegistry.getAll().size());
        table(out, "brooms", () -> at.koopro.wizardsandbeasts.broom.rules.BroomRules.authoredAll().size());
        table(out, "locations", () -> at.koopro.wizardsandbeasts.location.Locations.all().size());
        table(out, "treasure_sites", () -> at.koopro.wizardsandbeasts.treasure.TreasureSites.all().size());
        table(out, "pocket_templates", () -> at.koopro.wizardsandbeasts.trunk.template.PocketTemplateRegistry.getAll().size());
        table(out, "deeds", at.koopro.wizardsandbeasts.standing.deed.DeedRegistry::count);
        table(out, "standing_gates", at.koopro.wizardsandbeasts.standing.gate.StandingGates::count);
        table(out, "spell_law", at.koopro.wizardsandbeasts.ministry.trace.SpellLawRegistry::count);
        table(out, "npc_case_templates", () -> at.koopro.wizardsandbeasts.ministry.trace.NpcCaseTemplates.all().size());
        table(out, "handbook_chapters", () -> at.koopro.wizardsandbeasts.handbook.HandbookChapterManager.serverChapters().size());

        out.section("checks");
        int misses = at.koopro.wizardsandbeasts.heritage.appearance.HeritageAppearanceRegistry.reportedMissCount();
        out.row("appearance_misses", misses, misses > 0 ? Tone.WARN : Tone.GOOD);
        long unknownSpells = at.koopro.wizardsandbeasts.spell.def.SpellReloadListener.loaded().keySet().stream()
                .filter(id -> at.koopro.wizardsandbeasts.spell.core.Spells.byId(id.toString()) == null
                        && at.koopro.wizardsandbeasts.spell.core.Spells.byId(id.getPath()) == null)
                .count();
        out.row("spell_definitions_unbound", unknownSpells, unknownSpells > 0 ? Tone.WARN : Tone.GOOD);
        out.note("data_note");
    }

    private static void table(ReportBuilder out, String name, IntSupplier count) {
        int n;
        try {
            n = count.getAsInt();
        } catch (RuntimeException e) {
            out.row(name, "error: " + e.getClass().getSimpleName(), Tone.BAD);
            return;
        }
        out.row(name, n, n == 0 ? Tone.BAD : Tone.NORMAL);
    }

    private static void modules(AdminReport.Request request, ReportBuilder out) {
        out.section("states");
        for (Module module : Module.values()) {
            ModuleState state = ModuleManager.state(module);
            out.literal(module.name().toLowerCase(Locale.ROOT), state.getSerializedName(),
                    state == ModuleState.ENABLED ? Tone.GOOD : state == ModuleState.DISABLED ? Tone.MUTED : Tone.WARN);
        }
        out.section("edges");
        for (ModuleDependencies.Edge edge : ModuleDependencies.all()) {
            boolean broken = edge.kind() == ModuleDependencies.Kind.REQUIRES && ModuleManager.isEnabled(edge.dependent())
                    && !ModuleManager.isEnabled(edge.dependency());
            out.literal(edge.dependent().name().toLowerCase(Locale.ROOT) + " → " + edge.dependency().name().toLowerCase(Locale.ROOT),
                    edge.kind().name().toLowerCase(Locale.ROOT) + (broken ? " · unmet" : ""),
                    broken ? Tone.BAD : Tone.NORMAL);
        }
    }
}
