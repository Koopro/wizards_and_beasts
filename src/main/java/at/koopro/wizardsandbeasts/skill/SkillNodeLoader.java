package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads skill node definitions from {@code data/<namespace>/skill_nodes/} on every server
 * resource reload, mirroring {@link at.koopro.wizardsandbeasts.broom.BroomDefinitionLoader}.
 * Registered via {@code AddServerReloadListenersEvent} in {@code WizardsAndBeastsMod}.
 *
 * <p>The node id is the JSON {@code id} field (a plain string, matching the string-keyed player
 * attachment), not the resource path. Validation (all log-and-continue — a datapack must not
 * hard-crash the load): duplicate ids keep the first definition; edges referencing unknown ids or
 * crossing audience webs are logged and dropped; nodes unreachable from any {@code root} node of
 * their web are warned about (they are unallocatable under adjacency rules).
 */
public final class SkillNodeLoader extends SimpleJsonResourceReloadListener<Skill> {
    public static final String DIRECTORY = "skill_nodes";

    private static final Logger LOGGER = LogUtils.getLogger();

    public SkillNodeLoader() {
        super(Skill.CODEC, FileToIdConverter.json(DIRECTORY));
    }

    @Override
    protected void apply(Map<Identifier, Skill> jsonEntries, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, Skill> byId = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Skill> entry : jsonEntries.entrySet()) {
            Skill skill = entry.getValue();
            Skill previous = byId.putIfAbsent(skill.getId(), skill);
            if (previous != null) {
                LOGGER.error("Duplicate skill node id '{}' (from {}); keeping the first definition",
                        skill.getId(), entry.getKey());
            }
        }

        // Edge validation + symmetrized adjacency (same drop rules as SkillTrees.Snapshot, with logging).
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        int droppedEdges = 0;
        for (Skill skill : byId.values()) {
            for (String edge : skill.getEdges()) {
                Skill other = byId.get(edge);
                if (other == null) {
                    LOGGER.error("Skill node '{}' declares edge to unknown node '{}' — edge dropped",
                            skill.getId(), edge);
                    droppedEdges++;
                    continue;
                }
                if (other == skill) {
                    LOGGER.error("Skill node '{}' declares a self-edge — edge dropped", skill.getId());
                    droppedEdges++;
                    continue;
                }
                if (other.getTree().getAudience() != skill.getTree().getAudience()) {
                    LOGGER.error("Skill node '{}' ({}) declares cross-audience edge to '{}' ({}) — edge dropped",
                            skill.getId(), skill.getTree().getAudience(), edge, other.getTree().getAudience());
                    droppedEdges++;
                    continue;
                }
                adjacency.computeIfAbsent(skill.getId(), k -> new LinkedHashSet<>()).add(other.getId());
                adjacency.computeIfAbsent(other.getId(), k -> new LinkedHashSet<>()).add(skill.getId());
            }
        }

        // Reachability: BFS per audience web from its root nodes over the surviving adjacency.
        Map<SkillTreeId.Audience, Integer> webSizes = new EnumMap<>(SkillTreeId.Audience.class);
        int unreachable = 0;
        for (SkillTreeId.Audience audience : SkillTreeId.Audience.values()) {
            Set<String> visited = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            int webSize = 0;
            for (Skill skill : byId.values()) {
                if (skill.getTree().getAudience() != audience) {
                    continue;
                }
                webSize++;
                if (skill.isRoot()) {
                    visited.add(skill.getId());
                    queue.add(skill.getId());
                }
            }
            if (webSize == 0) {
                continue;
            }
            webSizes.put(audience, webSize);
            while (!queue.isEmpty()) {
                for (String neighbor : adjacency.getOrDefault(queue.poll(), Set.of())) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            for (Skill skill : byId.values()) {
                if (skill.getTree().getAudience() == audience && !visited.contains(skill.getId())) {
                    LOGGER.warn("Skill node '{}' is unreachable from any root of the {} web — unallocatable",
                            skill.getId(), audience);
                    unreachable++;
                }
            }
        }

        // Empty-web tripwire: a heritage a player can actually commit to must resolve to an audience that
        // has content. Node-less audiences are expected and inert for coming-soon heritages (veela, giant,
        // centaur, merpeople) — but a committable heritage pointing at an empty web is a shipping bug.
        // Warn loudly; never crash (a datapack that strips the wizard web shouldn't kill the server).
        for (HeritageVariant variant : HeritageVariant.values()) {
            if (!variant.getParentHeritage().isAlphaAvailable()) {
                continue;
            }
            SkillTreeId.Audience audience = SkillTreeId.audienceForVariant(variant);
            if (webSizes.getOrDefault(audience, 0) == 0) {
                LOGGER.warn("Committable heritage '{}/{}' resolves to audience {} which has ZERO skill nodes "
                                + "— players of this heritage would open an empty chart",
                        variant.getParentHeritage().getId(), variant.getId(), audience);
            }
        }

        SkillTrees.replaceAll(byId.values());
        LOGGER.info("Loaded {} skill nodes across {} webs ({} dropped edges, {} unreachable nodes)",
                byId.size(), webSizes.size(), droppedEdges, unreachable);
    }
}
