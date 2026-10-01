package at.koopro.wizardsandbeasts.client.admin.search;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.AdminLangKeys;
import at.koopro.wizardsandbeasts.admin.player.PlayerAdminService;
import at.koopro.wizardsandbeasts.client.admin.AdminClientRequests;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminBrewState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminBroomState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminCreatureState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminPlayerState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminProfileState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminVisualState;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminWandState;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Entry;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Kind;
import at.koopro.wizardsandbeasts.client.admin.search.AdminSearchIndex.Target;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleIds;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSessionInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns what the client already holds — the settings snapshot and each section's last reply — into search entries.
 * Nothing here asks the server for anything except {@link #requestMissing}, which fetches each section list once
 * when the search opens (and only for sections this administrator may see), so a first search can find a creature
 * without the Creatures page having been visited.
 */
@NullMarked
public final class AdminSearchSources {

    private AdminSearchSources() {}

    /** Asks for every list the index draws on that has not arrived yet. Cheap to call repeatedly: stale flags gate it. */
    public static void requestMissing() {
        if (allowed(AdminCategory.MAGIC) && ClientAdminState.spellListStale()) {
            AdminClientRequests.spellList();
        }
        if (allowed(AdminCategory.CREATURES) && ClientAdminCreatureState.listStale()) {
            AdminClientRequests.creatureList();
        }
        if (allowed(AdminCategory.BREWING) && ClientAdminBrewState.listStale()) {
            AdminClientRequests.brewList();
        }
        if (allowed(AdminCategory.WANDS) && ClientAdminWandState.stale()) {
            AdminClientRequests.wandCatalog();
        }
        if (allowed(AdminCategory.TRAVEL) && ClientAdminBroomState.stale()) {
            AdminClientRequests.broomList();
        }
        if (allowed(AdminCategory.VISUALS) && ClientAdminVisualState.stale()) {
            AdminClientRequests.beamList();
        }
        if (allowed(AdminCategory.PROFILES) && ClientAdminProfileState.stale()) {
            AdminClientRequests.profileList();
        }
    }

    private static boolean allowed(AdminCategory section) {
        AdminSessionInfo info = ClientAdminState.info();
        return info != null && info.capabilities().contains(section.defaultCapability());
    }

    /**
     * Changes whenever any source does: replies replace their lists (so identity is enough), settings bump
     * {@link ClientAdminState#version()}, and a language switch changes every title.
     */
    public static long signature() {
        long h = ClientAdminState.version();
        h = 31 * h + System.identityHashCode(ClientAdminState.spellList());
        h = 31 * h + System.identityHashCode(ClientAdminCreatureState.creatures());
        h = 31 * h + System.identityHashCode(ClientAdminBrewState.brews());
        h = 31 * h + System.identityHashCode(ClientAdminWandState.catalog());
        h = 31 * h + System.identityHashCode(ClientAdminBroomState.brooms());
        h = 31 * h + System.identityHashCode(ClientAdminVisualState.beams());
        h = 31 * h + System.identityHashCode(ClientAdminVisualState.presets());
        h = 31 * h + System.identityHashCode(ClientAdminProfileState.profiles());
        h = 31 * h + System.identityHashCode(ClientAdminPlayerState.rows());
        h = 31 * h + Minecraft.getInstance().getLanguageManager().getSelected().hashCode();
        return h;
    }

    public static List<Entry> build() {
        List<Entry> out = new ArrayList<>();
        sections(out);
        Map<String, String> creatureNames = creatures(out);
        settings(out, creatureNames);
        spells(out);
        brews(out);
        heritages(out);
        wandParts(out);
        brooms(out);
        beams(out);
        modules(out);
        profiles(out);
        players(out);
        return out;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String kind(Kind kind) {
        return tr(kind.labelKey());
    }

    private static void sections(List<Entry> out) {
        for (AdminCategory section : AdminCategory.values()) {
            out.add(Entry.of(Kind.SECTION, tr(section.nameKey()), tr(section.summaryKey()), new Target.Section(section),
                    section.id()));
        }
    }

    /** @return creature id → display name, for naming variant settings */
    private static Map<String, String> creatures(List<Entry> out) {
        Map<String, String> names = new HashMap<>();
        for (AdminCreaturePayloads.CreatureSummary creature : ClientAdminCreatureState.creatures()) {
            String name = tr(creature.nameKey());
            names.put(creature.id(), name);
            out.add(Entry.of(Kind.CREATURE, name, kind(Kind.CREATURE) + " · " + AdminSearchIndex.humanise(creature.category()),
                    new Target.Creature(creature.id()), creature.id(), creature.temperament()));
        }
        return names;
    }

    private static void settings(List<Entry> out, Map<String, String> creatureNames) {
        for (AdminSettingDescriptor setting : ClientAdminState.all()) {
            String path = setting.id().getPath();
            String name = tr(setting.nameKey());
            String section = tr(setting.category().nameKey());
            Target target = new Target.Setting(setting.id(), setting.category());
            if (!AdminLangKeys.entityScoped(path)) {
                out.add(Entry.of(Kind.SETTING, name, section, target, path, tr(setting.descriptionKey())));
                continue;
            }
            // creature/<id>/variant/<variant>/<property>, heritage/<id>/<property> …: name the thing it belongs to.
            String[] parts = path.split("/");
            String owner = parts.length > 1 ? creatureNames.getOrDefault(parts[1], AdminSearchIndex.humanise(parts[1])) : "";
            if (parts.length >= 5 && "variant".equals(parts[2])) {
                String variant = AdminSearchIndex.humanise(parts[3]);
                out.add(Entry.of(Kind.VARIANT, owner + " · " + variant + " — " + name, section, target, path));
            } else {
                out.add(Entry.of(Kind.SETTING, owner + " — " + name, section, target, path, tr(setting.descriptionKey())));
            }
        }
    }

    private static void spells(List<Entry> out) {
        for (AdminSpellSummary spell : ClientAdminState.spellList()) {
            out.add(Entry.of(Kind.SPELL, spell.displayName(), kind(Kind.SPELL) + " · " + AdminSearchIndex.humanise(spell.category()),
                    new Target.Spell(spell.id()), spell.id(), spell.castType(), spell.summary()));
        }
    }

    private static void brews(List<Entry> out) {
        for (AdminBrewPayloads.BrewSummary brew : ClientAdminBrewState.brews()) {
            // Effects are keywords: "phoenix" finds a brew made for phoenix tears, "healing" every healing draught.
            String[] keywords = new String[brew.effects().size() + 2];
            keywords[0] = brew.id();
            keywords[1] = brew.effectText();
            for (int i = 0; i < brew.effects().size(); i++) {
                keywords[i + 2] = brew.effects().get(i);
            }
            out.add(Entry.of(Kind.BREW, tr(brew.nameKey()), kind(Kind.BREW) + " · " + brew.effectText(),
                    new Target.Brew(brew.id()), keywords));
        }
    }

    private static void heritages(List<Entry> out) {
        for (Heritage heritage : Heritage.values()) {
            out.add(Entry.of(Kind.HERITAGE, heritage.getDisplayName(), kind(Kind.HERITAGE), new Target.Heritage(heritage.getId()),
                    heritage.getId()));
        }
    }

    private static void wandParts(List<Entry> out) {
        WandAdminService.Catalog catalog = ClientAdminWandState.catalog();
        if (catalog == null) {
            return;
        }
        for (AdminWandPayloads.PartInfo wood : catalog.woods()) {
            out.add(Entry.of(Kind.WAND_PART, partName(wood), tr("admin.wizards_and_beasts.search.wand_wood"),
                    new Target.WandPart(false, wood.id()), wood.id(), wood.lore()));
        }
        for (AdminWandPayloads.PartInfo core : catalog.cores()) {
            out.add(Entry.of(Kind.WAND_PART, partName(core), tr("admin.wizards_and_beasts.search.wand_core"),
                    new Target.WandPart(true, core.id()), core.id(), core.lore()));
        }
    }

    private static String partName(AdminWandPayloads.PartInfo part) {
        return part.nameTranslatable() ? tr(part.name()) : part.name();
    }

    private static void brooms(List<Entry> out) {
        for (AdminBroomPayloads.BroomSummary broom : ClientAdminBroomState.brooms()) {
            out.add(Entry.of(Kind.BROOM, tr(broom.nameKey()), kind(Kind.BROOM) + " · " + AdminSearchIndex.humanise(broom.tier()),
                    new Target.Broom(broom.id()), broom.id()));
        }
    }

    private static void beams(List<Entry> out) {
        for (BeamVisualAdminService.BeamSummary beam : ClientAdminVisualState.beams()) {
            Spell spell = Spells.byId(beam.spell());
            String name = spell == null ? beam.spell() : GuiText.resolve(spell.getDisplayName());
            out.add(Entry.of(Kind.BEAM, name, kind(Kind.BEAM), new Target.Beam(beam.spell()), beam.spell(), beam.preset()));
        }
        for (BeamPreset preset : ClientAdminVisualState.presets()) {
            out.add(Entry.of(Kind.BEAM_PRESET, preset.name(), kind(Kind.BEAM_PRESET), new Target.Beam(""), preset.id()));
        }
    }

    private static void modules(List<Entry> out) {
        for (Module module : Module.values()) {
            String id = ModuleIds.of(module).getPath();
            out.add(Entry.of(Kind.MODULE, ModuleIds.displayName(module).getString(), kind(Kind.MODULE), new Target.Module(id), id));
        }
    }

    private static void profiles(List<Entry> out) {
        for (AdminProfilePayloads.ProfileRow profile : ClientAdminProfileState.profiles()) {
            out.add(Entry.of(Kind.PROFILE, profile.name(), kind(Kind.PROFILE) + " · " + profile.author(),
                    new Target.Profile(profile.id()), profile.description(), profile.id()));
        }
    }

    private static void players(List<Entry> out) {
        for (PlayerAdminService.PlayerRow player : ClientAdminPlayerState.rows()) {
            out.add(Entry.of(Kind.PLAYER, player.name(), kind(Kind.PLAYER) + " · " + player.heritage(),
                    new Target.Player(player.id().toString())));
        }
    }
}
