package at.koopro.wizardsandbeasts.skill.build;

import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Live entry points for builds: the loaded archetypes and the profile of a player.
 *
 * <p>Archetypes are read from {@link VocationRegistry}, which the server fills from the datapack and the client
 * fills from {@code SyncVocationDefinitionsPayload} — so the same call answers on both sides. Server and client
 * differ only in where the web's adjacency comes from ({@link SkillTrees#neighbors} vs
 * {@link SkillTrees#clientNeighbors}).
 */
@NullMarked
public final class Builds {

    private Builds() {}

    /** Every vocation that is a build, in registry order. */
    public static List<Archetype> all() {
        return VocationRegistry.all().stream()
                .map(VocationDefinition::archetype)
                .flatMap(Optional::stream)
                .toList();
    }

    /** The server-side profile of {@code player}'s current allocation. */
    public static BuildProfile profileOf(ServerPlayer player) {
        return BuildProfile.compute(
                SkillSystemAPI.getSkillData(player).getUnlockedSkills(),
                all(),
                VocationHelper.getPrimary(player),
                SkillTrees::neighbors,
                id -> {
                    Skill skill = SkillTrees.byId(id);
                    return skill != null && skill.isRoot();
                },
                ModuleManager::isEnabled);
    }

    /** The client-side profile, from the synced allocation and the synced web. */
    public static BuildProfile clientProfile(Map<String, Integer> allocated, Optional<Identifier> declared) {
        return BuildProfile.compute(
                allocated,
                all(),
                declared,
                SkillTrees::clientNeighbors,
                id -> {
                    Skill skill = SkillTrees.clientById(id);
                    return skill != null && skill.isRoot();
                },
                ModuleManager::isEnabled);
    }

    /** Nodes cut off from the web if {@code nodeId} lost a level now (server adjacency). */
    public static Set<String> strandedBy(String nodeId, Map<String, Integer> allocated) {
        return WebConnectivity.strandedBy(nodeId, allocated, SkillTrees::neighbors,
                id -> {
                    Skill skill = SkillTrees.byId(id);
                    return skill != null && skill.isRoot();
                },
                id -> SkillTrees.byId(id) != null);
    }

    /** Client twin of {@link #strandedBy}, for the GUI's "can I unlearn this" hint. */
    public static Set<String> clientStrandedBy(String nodeId, Map<String, Integer> allocated) {
        return WebConnectivity.strandedBy(nodeId, allocated, SkillTrees::clientNeighbors,
                id -> {
                    Skill skill = SkillTrees.clientById(id);
                    return skill != null && skill.isRoot();
                },
                id -> SkillTrees.clientById(id) != null);
    }

    /** Every build a node is part of, with its tier there, in registry order (for tooltips). */
    public static List<Map.Entry<Archetype, BuildTier>> tagsOf(String nodeId) {
        List<Map.Entry<Archetype, BuildTier>> tags = new java.util.ArrayList<>();
        for (Archetype archetype : all()) {
            archetype.tierOf(nodeId).ifPresent(tier -> tags.add(Map.entry(archetype, tier)));
        }
        return tags;
    }
}
