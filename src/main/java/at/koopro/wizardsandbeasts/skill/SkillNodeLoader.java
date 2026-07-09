package at.koopro.wizardsandbeasts.skill;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads skill node definitions from {@code data/<namespace>/skill_nodes/} on every server
 * resource reload, mirroring {@link at.koopro.wizardsandbeasts.broom.BroomDefinitionLoader}.
 * Registered via {@code AddServerReloadListenersEvent} in {@code WizardsAndBeastsMod}.
 *
 * <p>The node id is the JSON {@code id} field (a plain string, matching the pre-datapack Java
 * registration and the string-keyed player attachment), not the resource path. Duplicate ids
 * and unknown prerequisites are logged; unknown prerequisites keep the node (an unsatisfiable
 * prerequisite locks the node rather than crashing the load).
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
        for (Skill skill : byId.values()) {
            for (String prereqId : skill.getPrerequisites()) {
                if (!byId.containsKey(prereqId)) {
                    LOGGER.error("Skill node '{}' has unknown prerequisite '{}' — node is unobtainable",
                            skill.getId(), prereqId);
                }
            }
        }
        SkillTrees.replaceAll(byId.values());
        LOGGER.info("Loaded {} skill node definitions", byId.size());
    }
}
