package at.koopro.wizardsandbeasts.admin.report;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.report.ReportView.Tone;
import at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * World section reports: what worldgen has actually done in this world, which magical locations the data defines and
 * how each is found, and the Floo network as registered. Read-only; worldgen itself is data and code.
 */
@NullMarked
final class WorldReports {

    private WorldReports() {}

    static void registerAll() {
        AdminReports.register(AdminReport.of("world_generation", AdminCategory.WORLD, WorldReports::generation));
        AdminReports.register(AdminReport.of("world_locations", AdminCategory.WORLD, WorldReports::locations));
        AdminReports.register(AdminReport.of("world_floo", AdminCategory.WORLD, WorldReports::floo));
    }

    private static void generation(AdminReport.Request request, ReportBuilder out) {
        MinecraftServer server = request.server();
        ServerLevel overworld = server.overworld();
        out.section("unique_structures");
        AzkabanWorldData azkaban = AzkabanWorldData.get(overworld);
        BlockPos center = azkaban.getCenter();
        out.flag("azkaban_module", ModuleManager.isEnabled(Module.AZKABAN), true);
        out.flag("azkaban_generated", azkaban.isGenerated(), true);
        out.row("azkaban_center", center == null ? "—" : center.toShortString());
        out.flag("chamber_module", ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS), true);
        out.note("chamber_note");

        out.section("structure_sets");
        var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        structures.listElementIds()
                .filter(key -> key.identifier().getNamespace().equals(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID))
                .sorted(Comparator.comparing(key -> key.identifier().getPath()))
                .forEach(key -> out.literal(key.identifier().getPath(), "registered"));

        out.section("dimensions");
        for (ServerLevel level : server.getAllLevels()) {
            out.literal(level.dimension().identifier().toString(), level.getChunkSource().getLoadedChunksCount()
                    + " chunks · " + level.players().size() + " players");
        }
        out.note("generation_note");
    }

    private static void locations(AdminReport.Request request, ReportBuilder out) {
        Map<Identifier, LocationDefinition> all = Locations.all();
        out.section("overview");
        out.row("defined", all.size(), all.isEmpty() ? Tone.WARN : Tone.NORMAL);
        long open = all.keySet().stream().filter(id -> Locations.open(id) != null).count();
        out.row("open", open);
        var professions = request.server().registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION);
        all.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            LocationDefinition definition = entry.getValue();
            out.literalSection(entry.getKey().getPath());
            out.row("module", definition.module().name().toLowerCase(Locale.ROOT),
                    ModuleManager.isEnabled(definition.module()) ? Tone.GOOD : Tone.WARN);
            out.row("papers", definition.papers().map(type -> type.getSerializedName()).orElse("—"));
            LocationDefinition.Presence presence = definition.presence();
            out.row("hosts", presence.hosts().isEmpty() ? "—" : String.join(", ",
                    presence.hosts().stream().map(Identifier::getPath).toList()));
            presence.hostProfession().ifPresent(id -> out.row("host_profession", id.toString(),
                    professions.containsKey(id) ? Tone.NORMAL : Tone.BAD));
            out.row("landmark", presence.landmark().map(Identifier::toString).orElse("—"));
            out.row("structure", presence.structure().map(Identifier::toString).orElse("—"));
            out.row("services", definition.services().size());
            out.flag("market", definition.market().isPresent(), true);
            out.flag("illicit", definition.risk().isPresent(), false);
        });
        out.section("how_found");
        out.note("locations_note");
    }

    private static void floo(AdminReport.Request request, ReportBuilder out) {
        FlooNetworkManager network = FlooNetworkManager.get(request.server().overworld());
        var entries = network.getAllEntries();
        out.section("overview");
        out.flag("floo_module", ModuleManager.isEnabled(Module.FLOO_NETWORK), true);
        out.row("fireplaces", entries.size());
        out.row("public", entries.stream().filter(FlooRegistryEntry::isPublic).count());
        out.row("disabled", entries.stream().filter(e -> !e.isEnabled()).count(),
                entries.stream().anyMatch(e -> !e.isEnabled()) ? Tone.WARN : Tone.NORMAL);
        out.section("fireplaces");
        boolean positions = request.actor().canModify(at.koopro.wizardsandbeasts.admin.access.AdminCapability.WORLD);
        entries.stream().sorted(Comparator.comparing(FlooRegistryEntry::networkAddress)).forEach(entry ->
                out.literal(entry.networkAddress(), (entry.isEnabled() ? "" : "disabled · ")
                                + (entry.isPublic() ? "public" : "private")
                                + (entry.tollKnuts() > 0 ? " · toll " + entry.tollKnuts() + " knuts" : "")
                                + (entry.isLandmark() ? " · landmark" : "")
                                + (positions ? " · " + entry.dimension().getPath() + " " + entry.blockPos().toShortString() : ""),
                        entry.isEnabled() ? Tone.NORMAL : Tone.MUTED));
        out.section("recent_travel");
        for (String line : network.getRecentLog(20)) {
            out.literalNote(line, Tone.NORMAL);
        }
    }
}
